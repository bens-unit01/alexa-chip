/** 
 * Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License"). You may not use this file 
 * except in compliance with the License. A copy of the License is located at
 *
 *   http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied. See the License for the 
 * specific language governing permissions and limitations under the License.
 */
package com.amazon.alexa.avs;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.sound.sampled.LineUnavailableException;

import org.apache.commons.fileupload.MultipartStream;
import org.eclipse.jetty.client.api.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.alexa.avs.AVSAudioPlayer.AlexaSpeechListener;
import com.amazon.alexa.avs.AlertManager.ResultListener;
import com.amazon.alexa.avs.auth.AccessTokenListener;
import com.amazon.alexa.avs.exception.DirectiveHandlingException;
import com.amazon.alexa.avs.exception.DirectiveHandlingException.ExceptionType;
import com.amazon.alexa.avs.http.AVSClient;
import com.amazon.alexa.avs.http.AVSClientFactory;
import com.amazon.alexa.avs.http.LinearRetryPolicy;
import com.amazon.alexa.avs.http.ParsingFailedHandler;
import com.amazon.alexa.avs.message.request.RequestBody;
import com.amazon.alexa.avs.message.request.RequestFactory;
import com.amazon.alexa.avs.message.response.Directive;
import com.amazon.alexa.avs.message.response.alerts.DeleteAlert;
import com.amazon.alexa.avs.message.response.alerts.SetAlert;
import com.amazon.alexa.avs.message.response.alerts.SetAlert.AlertType;
import com.amazon.alexa.avs.message.response.audioplayer.ClearQueue;
import com.amazon.alexa.avs.message.response.audioplayer.Play;
import com.amazon.alexa.avs.message.response.speaker.SetMute;
import com.amazon.alexa.avs.message.response.speaker.VolumePayload;
import com.amazon.alexa.avs.message.response.speechsynthesizer.Speak;
import com.amazon.alexa.avs.wakeword.WakeWordDetectedHandler;
import com.amazon.alexa.avs.wakeword.WakeWordIPC;
import com.amazon.alexa.avs.wakeword.WakeWordIPC.IPCCommand;
import com.amazon.alexa.avs.wakeword.WakeWordIPCFactory;

public class AVSController implements RecordingStateListener, AlertHandler, AlertEventListener,
        AccessTokenListener, DirectiveDispatcher, AlexaSpeechListener, ParsingFailedHandler,
        UserActivityListener, WakeWordDetectedHandler {

    private AudioCapture microphone;
    private final AVSClient avsClient;
    private final DialogRequestIdAuthority dialogRequestIdAuthority;
    private AlertManager alertManager;
    private boolean eventRunning = false; // is an event currently being sent

    private static final AudioInputFormat AUDIO_TYPE = AudioInputFormat.LPCM;
    private static final String START_SOUND = "start.mp3";
    private static final String END_SOUND = "stop.mp3";
    private static final String ERROR_SOUND = "error.mp3";
    private static final SpeechProfile PROFILE = SpeechProfile.NEAR_FIELD;
    private static final String FORMAT = "AUDIO_L16_RATE_16000_CHANNELS_1";

    private static final Logger log = LoggerFactory.getLogger(AVSController.class);
    private static final long MILLISECONDS_PER_SECOND = 1000;
    private static final long USER_INACTIVITY_REPORT_PERIOD_HOURS = 1;

    private final AVSAudioPlayer player;
    private BlockableDirectiveThread dependentDirectiveThread;
    private BlockableDirectiveThread independentDirectiveThread;
    private BlockingQueue<Directive> dependentQueue;
    private BlockingQueue<Directive> independentQueue;
    public SpeechRequestAudioPlayerPauseController speechRequestAudioPlayerPauseController;

    private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

    private AtomicLong lastUserInteractionTimestampSeconds;

    private final Set<ExpectSpeechListener> expectSpeechListeners;
    private ExpectStopCaptureListener stopCaptureHandler;

    private boolean wakeWordAgentEnabled = false;

    private WakeWordIPC wakeWordIPC = null;
    private boolean acceptWakeWordEvents = true; // to ensure we only process one event at a time
    private WakeWordDetectedHandler wakeWordDetectedHandler;
    private final int WAKE_WORD_AGENT_PORT_NUMBER = 5123;
    private final int WAKE_WORD_RELEASE_TRIES = 5;
    private final int WAKE_WORD_RELEASE_RETRY_DELAY_MS = 1000;

    public AVSController(ExpectSpeechListener listenHandler, AVSAudioPlayerFactory audioFactory,
            AlertManagerFactory alarmFactory, AVSClientFactory avsClientFactory,
            DialogRequestIdAuthority dialogRequestIdAuthority, boolean wakeWordAgentEnabled,
            WakeWordIPCFactory wakewordIPCFactory, WakeWordDetectedHandler wakeWakeDetectedHandler)
            throws Exception {

        this.wakeWordAgentEnabled = wakeWordAgentEnabled;
        this.wakeWordDetectedHandler = wakeWakeDetectedHandler;

        if (this.wakeWordAgentEnabled) {
            try {
                log.info("Creating Wake Word IPC | port number: " + WAKE_WORD_AGENT_PORT_NUMBER);
                this.wakeWordIPC =
                        wakewordIPCFactory.createWakeWordIPC(this, WAKE_WORD_AGENT_PORT_NUMBER);
                this.wakeWordIPC.init();
                Thread.sleep(1000);
                log.info("Created Wake Word IPC ok.");
            } catch (IOException e) {
                log.error("Error creating Wake Word IPC ok.", e);
            }
        }

        initializeMicrophone();

        this.player = audioFactory.getAudioPlayer(this);
        this.player.registerAlexaSpeechListener(this);
        this.dialogRequestIdAuthority = dialogRequestIdAuthority;
        speechRequestAudioPlayerPauseController =
                new SpeechRequestAudioPlayerPauseController(player);

        expectSpeechListeners =
                new HashSet<ExpectSpeechListener>(Arrays.asList(listenHandler,
                        speechRequestAudioPlayerPauseController));
        dependentQueue = new LinkedBlockingDeque<>();

        independentQueue = new LinkedBlockingDeque<>();

        DirectiveEnqueuer directiveEnqueuer =
                new DirectiveEnqueuer(dialogRequestIdAuthority, dependentQueue, independentQueue);

        avsClient = avsClientFactory.getAVSClient(directiveEnqueuer, this);

        alertManager = alarmFactory.getAlertManager(this, this, AlertsFileDataStore.getInstance());

        // Ensure that we have attempted to finish loading all alarms from file before sending
        // synchronize state
        alertManager.loadFromDisk(new ResultListener() {
            @Override
            public void onSuccess() {
                sendSynchronizeStateEvent();
            }

            @Override
            public void onFailure() {
                sendSynchronizeStateEvent();
            }
        });

        // ensure we notify AVS of playbackStopped on app exit
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                player.stop();
                avsClient.shutdown();
            }
        });

        dependentDirectiveThread =
                new BlockableDirectiveThread(dependentQueue, this, "DependentDirectiveThread");
        independentDirectiveThread =
                new BlockableDirectiveThread(independentQueue, this, "IndependentDirectiveThread");

        lastUserInteractionTimestampSeconds =
                new AtomicLong(System.currentTimeMillis() / MILLISECONDS_PER_SECOND);
        scheduledExecutor.scheduleAtFixedRate(new UserInactivityReport(),
                USER_INACTIVITY_REPORT_PERIOD_HOURS, USER_INACTIVITY_REPORT_PERIOD_HOURS,
                TimeUnit.HOURS);
    }

    private void getMicrophone(AVSController controller) throws LineUnavailableException {
        controller.microphone =
                AudioCapture.getAudioHardware(AUDIO_TYPE.getAudioFormat(),
                        new MicrophoneLineFactory());
    }
    
    private void initializeMicrophone() {

        if (this.wakeWordAgentEnabled) {
            AVSController controller = this;
            Callable<Void> task = new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    try {
                        wakeWordIPC.sendCommand(IPCCommand.IPC_PAUSE_WAKE_WORD_ENGINE);
                        getMicrophone(controller);
                    } catch (LineUnavailableException e) {
                        log.warn("Could not open microphone line");
                    }
                    wakeWordIPC.sendCommand(IPCCommand.IPC_RESUME_WAKE_WORD_ENGINE);
                    return null;
                }
            };

            try {
                LinearRetryPolicy retryPolicy =
                        new LinearRetryPolicy(WAKE_WORD_RELEASE_RETRY_DELAY_MS,
                                WAKE_WORD_RELEASE_TRIES);
                retryPolicy.tryCall(task, Exception.class);
            } catch (Exception e) {
                log.error("There was a problem connecting to the wake word engine.", e);
            }
        }

        // if either the wake-word agent is not configured for this platform, or we were not
        // able to connect to it, let's get the microphone directly
        if (microphone == null) {
            try {
                getMicrophone(this);
            } catch (LineUnavailableException e) {
                log.warn("Could not open the microphone line.");
            }
        }
    }

    public void startHandlingDirectives() {
        dependentDirectiveThread.start();
        independentDirectiveThread.start();
    }

    public void initializeStopCaptureHandler(ExpectStopCaptureListener stopHandler) {
        stopCaptureHandler = stopHandler;
    }

    public void sendSynchronizeStateEvent() {
        sendRequest(RequestFactory.createSystemSynchronizeStateEvent(player.getPlaybackState(),
                player.getSpeechState(), alertManager.getState(), player.getVolumeState()));
    }

    @Override
    public void onAccessTokenReceived(String accessToken) {
        avsClient.setAccessToken(accessToken);
    }

    // start the recording process and send to server
    // takes an optional RMS callback and an optional request callback
    public void startRecording(RecordingRMSListener rmsListener, RequestListener requestListener) {

        if (this.wakeWordAgentEnabled) {

            acceptWakeWordEvents = false;

            try {
                wakeWordIPC.sendCommand(IPCCommand.IPC_PAUSE_WAKE_WORD_ENGINE);
            } catch (IOException e) {
                log.warn("Could not send the IPC_PAUSE_WAKE_WORD_ENGINE command");
            }
        }

        try {
            String dialogRequestId = dialogRequestIdAuthority.createNewDialogRequestId();

            RequestBody body =
                    RequestFactory.createSpeechRecognizerRecognizeRequest(dialogRequestId, PROFILE,
                            FORMAT, player.getPlaybackState(), player.getSpeechState(),
                            alertManager.getState(), player.getVolumeState());
  
            dependentQueue.clear();

            InputStream inputStream = getMicrophoneInputStream(this, rmsListener);

            avsClient.sendEvent(body, inputStream, requestListener, AUDIO_TYPE);

            System.out.println("[AVSController#startRecording--]");

            speechRequestAudioPlayerPauseController.startSpeechRequest();
        } catch (Exception e) {
            player.playMp3FromResource(ERROR_SOUND);
            requestListener.onRequestError(e);
        }
    }

    private InputStream getMicrophoneInputStream(AVSController controller,
            RecordingRMSListener rmsListener) throws LineUnavailableException, IOException {

        int numberRetries = 1;

        if (this.wakeWordAgentEnabled) {
            numberRetries = WAKE_WORD_RELEASE_TRIES;
        }

        for(; numberRetries > 0; numberRetries--) {
            try {
                return microphone.getAudioInputStream(controller, rmsListener);
            } catch (LineUnavailableException | IOException e) {
                if (numberRetries == 1) {
                    throw e;
                }
                log.warn("Could not open the microphone line.");
                try {
                    Thread.sleep(WAKE_WORD_RELEASE_RETRY_DELAY_MS);
                } catch (InterruptedException e1) {
                    log.error("exception:", e1);
                }
            }
        }

        throw new LineUnavailableException();
    }

    public void handlePlaybackAction(PlaybackAction action) {
        switch (action) {
            case PLAY:
                if (alertManager.hasActiveAlerts()) {
                    alertManager.stopActiveAlert();
                } else {
                    sendRequest(RequestFactory.createPlaybackControllerPlayEvent(
                            player.getPlaybackState(), player.getSpeechState(),
                            alertManager.getState(), player.getVolumeState()));
                }
                break;
            case PAUSE:
                if (alertManager.hasActiveAlerts()) {
                    alertManager.stopActiveAlert();
                } else {
                    sendRequest(RequestFactory.createPlaybackControllerPauseEvent(
                            player.getPlaybackState(), player.getSpeechState(),
                            alertManager.getState(), player.getVolumeState()));
                }
                break;
            case PREVIOUS:
                sendRequest(RequestFactory.createPlaybackControllerPreviousEvent(
                        player.getPlaybackState(), player.getSpeechState(),
                        alertManager.getState(), player.getVolumeState()));
                break;
            case NEXT:
                sendRequest(RequestFactory.createPlaybackControllerNextEvent(
                        player.getPlaybackState(), player.getSpeechState(),
                        alertManager.getState(), player.getVolumeState()));
                break;
            default:
                log.error("Failed to handle playback action");
        }
    }

    public void sendRequest(RequestBody body) {
        eventRunning = true;
        try {
            avsClient.sendEvent(body);
        } catch (Exception e) {
            log.error("Failed to send request", e);
        }
        eventRunning = false;
    }

    public boolean eventRunning() {
        return eventRunning;
    }

    @Override
    public synchronized void dispatch(Directive directive) {
        String directiveNamespace = directive.getNamespace();

        String directiveName = directive.getName();
        log.info("Handling directive: {}.{}", directiveNamespace, directiveName);
        System.out.println("[AVSController#dispatch] directive : " + directiveNamespace + " " + directiveName);
        if (dialogRequestIdAuthority.isCurrentDialogRequestId(directive.getDialogRequestId())) {
            speechRequestAudioPlayerPauseController.dispatchDirective();
        }
        try {
            if (directiveNamespace.equals(AVSAPIConstants.SpeechRecognizer.NAMESPACE)) {
                handleSpeechRecognizerDirective(directive);
            } else if (directiveNamespace.equals(AVSAPIConstants.SpeechSynthesizer.NAMESPACE)) {
                handleSpeechSynthesizerDirective(directive);
            } else if (directiveNamespace.equals(AVSAPIConstants.AudioPlayer.NAMESPACE)) {
                handleAudioPlayerDirective(directive);
            } else if (directiveNamespace.equals(AVSAPIConstants.Alerts.NAMESPACE)) {
                handleAlertsDirective(directive);
            } else if (directiveNamespace.equals(AVSAPIConstants.Speaker.NAMESPACE)) {
                handleSpeakerDirective(directive);
            } else if (directiveNamespace.equals(AVSAPIConstants.System.NAMESPACE)) {
                handleSystemDirective(directive);
            } else {
                throw new DirectiveHandlingException(ExceptionType.UNSUPPORTED_OPERATION,
                        "No device side component to handle the directive.");
            }
        } catch (DirectiveHandlingException e) {
            sendExceptionEncounteredEvent(directive.getRawMessage(), e.getType(), e);
        } catch (Exception e) {
            sendExceptionEncounteredEvent(directive.getRawMessage(), ExceptionType.INTERNAL_ERROR,
                    e);
            throw e;
        }

    }

    private void sendExceptionEncounteredEvent(String directiveJson, ExceptionType type, Exception e) {
        sendRequest(RequestFactory.createSystemExceptionEncounteredEvent(directiveJson, type,
                e.getMessage(), player.getPlaybackState(), player.getSpeechState(),
                alertManager.getState(), player.getVolumeState()));
        log.error("{} error handling directive: {}", type, directiveJson, e);
    }

    private void handleAudioPlayerDirective(Directive directive) throws DirectiveHandlingException {
        String directiveName = directive.getName();
        if (directiveName.equals(AVSAPIConstants.AudioPlayer.Directives.Play.NAME)) {
            player.handlePlay((Play) directive.getPayload());
        } else if (directiveName.equals(AVSAPIConstants.AudioPlayer.Directives.Stop.NAME)) {
            player.handleStop();
        } else if (directiveName.equals(AVSAPIConstants.AudioPlayer.Directives.ClearQueue.NAME)) {
            player.handleClearQueue((ClearQueue) directive.getPayload());
        }

    }

    private void handleSpeechSynthesizerDirective(Directive directive)
            throws DirectiveHandlingException {
        if (directive.getName().equals(AVSAPIConstants.SpeechSynthesizer.Directives.Speak.NAME)) {
            player.handleSpeak((Speak) directive.getPayload());
        }
    }

    private void handleSpeechRecognizerDirective(Directive directive) {
        if (directive.getName().equals(
                AVSAPIConstants.SpeechRecognizer.Directives.ExpectSpeech.NAME)) {

            // If your device cannot handle automatically starting to listen, you must
            // implement a listen timeout event, as described here:
            // https://developer.amazon.com/public/solutions/alexa/alexa-voice-service/rest/speechrecognizer-listentimeout-request
            notifyExpectSpeechDirective();
        } else if (directive.getName().equals(
                AVSAPIConstants.SpeechRecognizer.Directives.StopCapture.NAME)) {
            stopCaptureHandler.onStopCaptureDirective();
        }
    }

    private void handleAlertsDirective(Directive directive) {
        String directiveName = directive.getName();
        if (directiveName.equals(AVSAPIConstants.Alerts.Directives.SetAlert.NAME)) {
            SetAlert payload = (SetAlert) directive.getPayload();
            String alertToken = payload.getToken();
            ZonedDateTime scheduledTime = payload.getScheduledTime();
            AlertType type = payload.getType();

            if (alertManager.hasAlert(alertToken)) {
                AlertScheduler scheduler = alertManager.getScheduler(alertToken);
                if (scheduler.getAlert().getScheduledTime().equals(scheduledTime)) {
                    return;
                } else {
                    scheduler.cancel();
                }
            }

            Alert alert = new Alert(alertToken, type, scheduledTime);
            alertManager.add(alert);
        } else if (directiveName.equals(AVSAPIConstants.Alerts.Directives.DeleteAlert.NAME)) {
            DeleteAlert payload = (DeleteAlert) directive.getPayload();
            alertManager.delete(payload.getToken());
        }
    }

    private void handleSpeakerDirective(Directive directive) {
        String directiveName = directive.getName();
        if (directiveName.equals(AVSAPIConstants.Speaker.Directives.SetVolume.NAME)) {
            player.handleSetVolume((VolumePayload) directive.getPayload());
        } else if (directiveName.equals(AVSAPIConstants.Speaker.Directives.AdjustVolume.NAME)) {
            player.handleAdjustVolume((VolumePayload) directive.getPayload());
        } else if (directiveName.equals(AVSAPIConstants.Speaker.Directives.SetMute.NAME)) {
            player.handleSetMute((SetMute) directive.getPayload());
        }
    }

    private void handleSystemDirective(Directive directive) {
        if (directive.getName().equals(AVSAPIConstants.System.Directives.ResetUserInactivity.NAME)) {
            onUserActivity();
        }
    }

    private void notifyExpectSpeechDirective() {
        for (ExpectSpeechListener listener : expectSpeechListeners) {
            listener.onExpectSpeechDirective();
        }
    }

    public void stopRecording() {
        speechRequestAudioPlayerPauseController.finishedListening();
        microphone.stopCapture();

        if (this.wakeWordAgentEnabled) {
            try {
                wakeWordIPC.sendCommand(IPCCommand.IPC_RESUME_WAKE_WORD_ENGINE);
            } catch (IOException e) {
                log.warn("could not send resume wake word engine command", e);
            }
            acceptWakeWordEvents = true;
        }
    }

    // audio state callback for when recording has started
    @Override
    public void recordingStarted() {
        player.playMp3FromResource(START_SOUND);
    }

    // audio state callback for when recording has completed
    @Override
    public void recordingCompleted() {
        player.playMp3FromResource(END_SOUND);
    }

    public boolean isSpeaking() {
        return player.isSpeaking();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    @Override
    public void onAlertStarted(String alertToken) {
        sendRequest(RequestFactory.createAlertsAlertStartedEvent(alertToken));

        if (player.isSpeaking()) {
            sendRequest(RequestFactory.createAlertsAlertEnteredBackgroundEvent(alertToken));
        } else {
            sendRequest(RequestFactory.createAlertsAlertEnteredForegroundEvent(alertToken));
        }
    }

    @Override
    public void onAlertStopped(String alertToken) {
        sendRequest(RequestFactory.createAlertsAlertStoppedEvent(alertToken));
    }

    @Override
    public void onAlertSet(String alertToken, boolean success) {
        sendRequest(RequestFactory.createAlertsSetAlertEvent(alertToken, success));
    }

    @Override
    public void onAlertDelete(String alertToken, boolean success) {
        sendRequest(RequestFactory.createAlertsDeleteAlertEvent(alertToken, success));
    }

    @Override
    public void startAlert(String alertToken) {
        player.startAlert();
    }

    @Override
    public void stopAlert(String alertToken) {
        if (!alertManager.hasActiveAlerts()) {
            player.stopAlert();
        }
    }

    public void processingFinished() {
        speechRequestAudioPlayerPauseController.speechRequestProcessingFinished(dependentQueue
                .size());
    }

    @Override
    public void onAlexaSpeechStarted() {
        dependentDirectiveThread.block();

        if (alertManager.hasActiveAlerts()) {
            for (String alertToken : alertManager.getActiveAlerts()) {
                sendRequest(RequestFactory.createAlertsAlertEnteredBackgroundEvent(alertToken));
            }
        }
    }

    @Override
    public void onAlexaSpeechFinished() {
        dependentDirectiveThread.unblock();

        if (alertManager.hasActiveAlerts()) {
            for (String alertToken : alertManager.getActiveAlerts()) {
                sendRequest(RequestFactory.createAlertsAlertEnteredForegroundEvent(alertToken));
            }
        }
    }

    @Override
    public void onParsingFailed(String unparseable) {
        String message = "Failed to parse message from AVS";
        sendRequest(RequestFactory.createSystemExceptionEncounteredEvent(unparseable,
                ExceptionType.UNEXPECTED_INFORMATION_RECEIVED, message, player.getPlaybackState(),
                player.getSpeechState(), alertManager.getState(), player.getVolumeState()));
    }

    @Override
    public void onUserActivity() {
        lastUserInteractionTimestampSeconds.set(System.currentTimeMillis()
                / MILLISECONDS_PER_SECOND);
    }

    private class UserInactivityReport implements Runnable {

        @Override
        public void run() {
            sendRequest(RequestFactory.createSystemUserInactivityReportEvent((System
                    .currentTimeMillis() / MILLISECONDS_PER_SECOND)
                    - lastUserInteractionTimestampSeconds.get()));
        }
    }

    @Override
    public synchronized void onWakeWordDetected() {
        if (acceptWakeWordEvents) {
            wakeWordDetectedHandler.onWakeWordDetected();
        }
    }
}
