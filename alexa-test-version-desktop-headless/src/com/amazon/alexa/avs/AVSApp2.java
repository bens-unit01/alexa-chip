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

import com.amazon.alexa.avs.auth.AccessTokenListener;
import com.amazon.alexa.avs.auth.AuthSetup;
import com.amazon.alexa.avs.auth.companionservice.RegCodeDisplayHandler;
import com.amazon.alexa.avs.config.DeviceConfig;
import com.amazon.alexa.avs.config.DeviceConfigUtils;
import com.amazon.alexa.avs.http.AVSClientFactory;
import com.amazon.alexa.avs.wakeword.WakeWordDetectedHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;


import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.amazon.alexa.avs.wakeword.WakeWordIPCFactory;
import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotTimeoutException;
import com.amazonaws.services.iot.client.sample.pubSub.PublishSubscribeSample;
import com.amazonaws.services.iot.client.sample.shadowEcho.ShadowEchoSample;

@SuppressWarnings("serial")
public class AVSApp2 extends JFrame implements ExpectSpeechListener, RecordingRMSListener,
        RegCodeDisplayHandler, AccessTokenListener, ExpectStopCaptureListener,
        WakeWordDetectedHandler {

    private static final Logger log = LoggerFactory.getLogger(AVSApp2.class);

    private static final String APP_TITLE = "Alexa Voice Service";
    private static final String LISTEN_LABEL = "Listen";
    private static final String PROCESSING_LABEL = "Processing";
    private static final String PREVIOUS_LABEL = "\u21E4";
    private static final String NEXT_LABEL = "\u21E5";
    private static final String PAUSE_LABEL = "\u275A\u275A";
    private static final String PLAY_LABEL = "\u25B6";
    private final AVSController controller;
    private JButton actionButton;
    private PushButton pushButton;  
    private JButton playPauseButton;
    private Container playbackPanel;
    private JTextField tokenTextField;
    private JProgressBar visualizer;
    private final DeviceConfig deviceConfig;
 
    private String accessToken;

    private AuthSetup authSetup;

    private enum ButtonState {
        START, STOP, PROCESSING;
    }

    private ButtonState buttonState;

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new AVSApp2(args[0]);
        } else {
            new AVSApp2();
        }
    }

    public AVSApp2() throws Exception {
        this(DeviceConfigUtils.readConfigFile());
    }

    public AVSApp2(String configName) throws Exception {
        this(DeviceConfigUtils.readConfigFile(configName));
    }

    private AVSApp2(DeviceConfig config) throws Exception {
    	System.out.println("app started ..."); 
       deviceConfig = config;
        controller =
                new AVSController(this, new AVSAudioPlayerFactory(), new AlertManagerFactory(),
                        getAVSClientFactory(deviceConfig), DialogRequestIdAuthority.getInstance(),
                        config.getWakeWordAgentEnabled(), new WakeWordIPCFactory(), this);
        

        new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					ShadowEchoSample.setIotListener(null);
//					PublishSubscribeSample.test(null);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (AWSIotException e) {
					e.printStackTrace();
				} catch (AWSIotTimeoutException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
		}).start(); 

        authSetup = new AuthSetup(config, this);
        authSetup.addAccessTokenListener(this);
        authSetup.addAccessTokenListener(controller);
        authSetup.startProvisioningThread();

        // IoT test 
        String[] args = new String[6];  
        
//        addDeviceField();
//        addTokenField();
//        addVisualizerField();
//        addActionField();
//        addPlaybackButtons();
//
//        getContentPane().setLayout(new GridLayout(0, 1));
//        setTitle(getAppTitle());
//        setDefaultCloseOperation(EXIT_ON_CLOSE);
//        setSize(400, 200);
//        setVisible(true);
        
        
        controller.initializeStopCaptureHandler(this);
        controller.startHandlingDirectives();
        buttonState = ButtonState.START;  
        final RecordingRMSListener rmsListener = this;
      pushButton = new PushButton(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
          controller.onUserActivity();
           System.out.println("actionPerformed ...");
          if (buttonState == ButtonState.START) { // if in idle mode
              buttonState = ButtonState.STOP;
//                  setPlaybackControlEnabled(false);

              RequestListener requestListener = new RequestListener() {

                  @Override
                  public void onRequestSuccess() {
                      finishProcessing();
                  }

                  @Override
                  public void onRequestError(Throwable e) {
                      log.error("An error occured creating speech request", e);
//                      JOptionPane.showMessageDialog(getContentPane(), e.getMessage(),
//                              "Error", JOptionPane.ERROR_MESSAGE);
//                      actionButton.doClick();
                      finishProcessing();
                  }
              };
              controller.startRecording(rmsListener, requestListener);
          } else { // else we must already be in listening
//              actionButton.setText(PROCESSING_LABEL); // go into processing mode
//              actionButton.setEnabled(false);
//              visualizer.setIndeterminate(true);
              buttonState = ButtonState.PROCESSING;
              controller.stopRecording(); // stop the recording so the request can complete
          }
      }
  });
      
      
    new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub

				try {
                  final BufferedReader br ;
					br = new BufferedReader(new InputStreamReader(System.in));
					while (true) {

						System.out.print("Enter something : ");
						String input = br.readLine();

						if ("a01".equals(input)) {
							pushButton.actionOccurred();
							System.out.println("push button ...");
						}
						if ("q".equals(input)) {
							System.out.println("Exit!");
							System.exit(0);
						}

						System.out.println("input : " + input);
						System.out.println("-----------\n");
					}

				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					
				}

			}
		}).start();
        
    }

    private String getAppVersion() {
       /* final Properties properties = new Properties();
        String c = getClass().toString(); 
        try (final InputStream stream = getClass().getResourceAsStream("/src/version.properties")) {
            properties.load(stream);
            if (properties.containsKey("version")) {
                return properties.getProperty("version");
            }
        } catch (IOException e) {
            log.warn("version.properties file not found on classpath");
        }
        */
        return "20160207.1";
    }

    private String getAppTitle() {
        String version = getAppVersion();
        String title = APP_TITLE;
        if (version != null) {
            title += " - v" + version;
        }
        return title;
    }

    protected AVSClientFactory getAVSClientFactory(DeviceConfig config) {
        return new AVSClientFactory(config);
    }

    private void addDeviceField() {
        JLabel productIdLabel = new JLabel(deviceConfig.getProductId());
        JLabel dsnLabel = new JLabel(deviceConfig.getDsn());
        productIdLabel.setFont(productIdLabel.getFont().deriveFont(Font.PLAIN));
        dsnLabel.setFont(dsnLabel.getFont().deriveFont(Font.PLAIN));

        FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
        flowLayout.setHgap(0);
        JPanel devicePanel = new JPanel(flowLayout);
        devicePanel.add(new JLabel("Device: "));
        devicePanel.add(productIdLabel);
        devicePanel.add(Box.createRigidArea(new Dimension(5, 0)));
        devicePanel.add(new JLabel("DSN: "));
        devicePanel.add(dsnLabel);
        getContentPane().add(devicePanel);
    }

    private void addTokenField() {
        getContentPane().add(new JLabel("Bearer Token:"));
        tokenTextField = new JTextField(50);
        tokenTextField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                controller.onUserActivity();
                authSetup.onAccessTokenReceived(tokenTextField.getText());
            }
        });
        getContentPane().add(tokenTextField);

        if (accessToken != null) {
            tokenTextField.setText(accessToken);
            accessToken = null;
        }
    }

    private void addVisualizerField() {
        visualizer = new JProgressBar(0, 100);
        getContentPane().add(visualizer);
    }

    private void addActionField() {
//        final RecordingRMSListener rmsListener = this;
//        //actionButton = new JButton(LISTEN_LABEL);
//        //buttonState = ButtonState.START;
//        //actionButton.setEnabled(true);
//        //actionButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                controller.onUserActivity();
//
//                if (buttonState == ButtonState.START) { // if in idle mode
//                    buttonState = ButtonState.STOP;
//                    setPlaybackControlEnabled(false);
//
//                    RequestListener requestListener = new RequestListener() {
//
//                        @Override
//                        public void onRequestSuccess() {
//                            finishProcessing();
//                        }
//
//                        @Override
//                        public void onRequestError(Throwable e) {
//                            log.error("An error occured creating speech request", e);
//                            JOptionPane.showMessageDialog(getContentPane(), e.getMessage(),
//                                    "Error", JOptionPane.ERROR_MESSAGE);
//                            actionButton.doClick();
//                            finishProcessing();
//                        }
//                    };
//                    controller.startRecording(rmsListener, requestListener);
//                } else { // else we must already be in listening
//                    actionButton.setText(PROCESSING_LABEL); // go into processing mode
//                    actionButton.setEnabled(false);
//                    visualizer.setIndeterminate(true);
//                    buttonState = ButtonState.PROCESSING;
//                    controller.stopRecording(); // stop the recording so the request can complete
//                }
//            }
//        });
//
//        getContentPane().add(actionButton);
    }

    /**
     * Respond to a music button press event
     *
     * @param action
     *            Playback action to handle
     */
    private void musicButtonPressedEventHandler(final PlaybackAction action) {
        SwingWorker<Void, Void> alexaCall = new SwingWorker<Void, Void>() {
            @Override
            public Void doInBackground() throws Exception {
                visualizer.setIndeterminate(true);
                controller.handlePlaybackAction(action);
                return null;
            }

            @Override
            public void done() {
                visualizer.setIndeterminate(false);
            }
        };
        alexaCall.execute();
    }

    private void createMusicButton(Container container, String label, final PlaybackAction action) {
        JButton button = new JButton(label);
        button.setEnabled(true);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                controller.onUserActivity();
                musicButtonPressedEventHandler(action);
            }
        });
        container.add(button);
    }

    private void setPlaybackControlEnabled(boolean enable) {
        setComponentsOfContainerEnabled(playbackPanel, enable);
    }

    /**
     * Recursively Enable/Disable components in a container
     *
     * @param container
     *            Object of type Container (like JPanel).
     * @param enable
     *            Set true to enable all components in the container. Set to false to disable all.
     */
    private void setComponentsOfContainerEnabled(Container container, boolean enable) {
        for (Component component : container.getComponents()) {
            if (component instanceof Container) {
                setComponentsOfContainerEnabled((Container) component, enable);
            }
            component.setEnabled(enable);
        }
    }

    /**
     * Add music control buttons
     */
    private void addPlaybackButtons() {
        playbackPanel = new JPanel();
        playbackPanel.setLayout(new GridLayout(1, 5));

        playPauseButton = new JButton(PLAY_LABEL + "/" + PAUSE_LABEL);
        playPauseButton.setEnabled(true);
        playPauseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                controller.onUserActivity();
                if (controller.isPlaying()) {
                    musicButtonPressedEventHandler(PlaybackAction.PAUSE);
                } else {
                    musicButtonPressedEventHandler(PlaybackAction.PLAY);
                }
            }
        });

        createMusicButton(playbackPanel, PREVIOUS_LABEL, PlaybackAction.PREVIOUS);
        playbackPanel.add(playPauseButton);

        createMusicButton(playbackPanel, NEXT_LABEL, PlaybackAction.NEXT);
        getContentPane().add(playbackPanel);
    }

    public void finishProcessing() {
        //actionButton.setText(LISTEN_LABEL);
        //setPlaybackControlEnabled(true);
        buttonState = ButtonState.START;
        //actionButton.setEnabled(true);
       //visualizer.setIndeterminate(false);
        controller.processingFinished();

    }

    @Override
    public void rmsChanged(int rms) { // AudioRMSListener callback
       // visualizer.setValue(rms); // update the visualizer
    }

    @Override
    public void onExpectSpeechDirective() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                while (!actionButton.isEnabled() || buttonState != ButtonState.START
                        || controller.isSpeaking()) {
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                    }
                }
                actionButton.doClick();
            }
        };
        thread.start();
    }

    @Override
    public void onStopCaptureDirective() {
        if (buttonState == ButtonState.STOP) {
            //actionButton.doClick();
            pushButton.actionOccurred(); 
        }
    }

    public int showYesNoDialog(String message, String title) {
        JTextArea textMessage = new JTextArea(message);
        textMessage.setEditable(false);
        return JOptionPane.showConfirmDialog(getContentPane(), textMessage, title,
                JOptionPane.YES_NO_OPTION);
    }

    public void showDialog(String message, String title) {
        JTextArea textMessage = new JTextArea(message);
        textMessage.setEditable(false);
        JOptionPane.showMessageDialog(getContentPane(), textMessage, title,
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void displayRegCode(String regCode) {
        String title = "Login to Register/Authenticate your Device";
        String regUrl =
                deviceConfig.getCompanionServiceInfo().getServiceUrl() + "/provision/" + regCode;
        int selected =
                showYesNoDialog(
                        "Please register your device by visiting the following URL in "
                                + "a web browser and follow the instructions:\n"
                                + regUrl
                                + "\n\n Would you like to open the URL automatically in your default browser?",
                        title);
        if (selected == JOptionPane.YES_OPTION) {
            try {
                Desktop.getDesktop().browse(new URI(regUrl));
            } catch (Exception e) {
                // Ignore and proceed
            }
            title = "Click OK after Registering/Authenticating Device";
            showDialog(
                    "If a browser window did not open, please copy and paste the below URL into a "
                            + "web browser, and follow the instructions:\n" + regUrl
                            + "\n\n Click the OK button when finished.", title);
        }
    }

    @Override
    public synchronized void onAccessTokenReceived(String accessToken) {
        if (tokenTextField == null) {
            this.accessToken = accessToken;
        } else {
            tokenTextField.setText(accessToken);
        }
    }

    @Override
    public synchronized void onWakeWordDetected() {
        if (buttonState == ButtonState.START) { // if in idle mode
            log.info("Wake Word was detected");
            actionButton.doClick();
        }
    }
}
