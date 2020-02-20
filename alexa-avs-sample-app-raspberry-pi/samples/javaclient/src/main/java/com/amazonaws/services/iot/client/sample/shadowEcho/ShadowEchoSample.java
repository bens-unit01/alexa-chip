/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.services.iot.client.sample.shadowEcho;

import java.io.IOException;

import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotDevice;
import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotTimeoutException;
import com.amazonaws.services.iot.client.AWSIotTopic;
import com.amazonaws.services.iot.client.sample.pubSub.TestTopicListener;
import com.amazonaws.services.iot.client.sample.sampleUtil.CommandArguments;
import com.amazonaws.services.iot.client.sample.sampleUtil.SampleUtil;
import com.amazonaws.services.iot.client.sample.sampleUtil.SampleUtil.KeyStorePasswordPair;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;



/**
 * This example demonstrates how to use {@link AWSIotDevice} to directly access
 * the shadow document.
 */
public class ShadowEchoSample {
   
    static GpioController gpio = GpioFactory.getInstance(); 
    private static AWSIotMqttClient awsIotClient;
    static GpioPinDigitalOutput pin1 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "PinLED", PinState.LOW);
    static GpioPinDigitalOutput pin2 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, "PinLED", PinState.LOW);
    static GpioPinDigitalOutput pin3 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, "PinLED", PinState.LOW);


    public static void setClient(AWSIotMqttClient client) {
        awsIotClient = client;
    }

    private static void initClient(CommandArguments arguments) {
        String clientEndpoint =  SampleUtil.getConfig("clientEndpoint");
        String clientId = SampleUtil.getConfig("clientId");

        String certificateFile =  SampleUtil.getConfig("certificateFile");
        String privateKeyFile =  SampleUtil.getConfig("privateKeyFile");
        if (awsIotClient == null && certificateFile != null && privateKeyFile != null) {
            String algorithm =  SampleUtil.getConfig("keyAlgorithm");
            KeyStorePasswordPair pair = SampleUtil.getKeyStorePasswordPair(certificateFile, privateKeyFile, algorithm);

            awsIotClient = new AWSIotMqttClient(clientEndpoint, clientId, pair.keyStore, pair.keyPassword);
        }

        if (awsIotClient == null) {
        	/*
            String awsAccessKeyId = arguments.get("awsAccessKeyId", SampleUtil.getConfig("awsAccessKeyId"));
            String awsSecretAccessKey = arguments.get("awsSecretAccessKey", SampleUtil.getConfig("awsSecretAccessKey"));
            String sessionToken = arguments.get("sessionToken", SampleUtil.getConfig("sessionToken"));

            if (awsAccessKeyId != null && awsSecretAccessKey != null) {
                awsIotClient = new AWSIotMqttClient(clientEndpoint, clientId, awsAccessKeyId, awsSecretAccessKey,
                        sessionToken);
            }
            */
        	System.out.println("erreur: awsIotClient non cree ...");
        }

        if (awsIotClient == null) {
            throw new IllegalArgumentException("Failed to construct client due to missing certificate or credentials.");
        }
    }

    public static void setIotListener(String args[]) throws IOException, AWSIotException, AWSIotTimeoutException,
            InterruptedException {
        //CommandArguments arguments = CommandArguments.parse(args);
        initClient(null);

        String thingName =  SampleUtil.getConfig("thingName");
        AWSIotDevice device = new AWSIotDevice(thingName);

        awsIotClient.attach(device);
        awsIotClient.connect();

        // Delete existing document if any
        device.delete();
        
        final AWSIotQos TestTopicQos = AWSIotQos.QOS0;
        String topic = "$aws/things/waterPump/shadow/update/delta"; 
//        String topic = "$aws/things/waterPump/shadow/update/accepted"; 
        AWSIotTopic ls = new TestTopicListener(topic, TestTopicQos);
        awsIotClient.subscribe(ls, true);



        while (true) {

/*
            try {
                device.update(jsonState);
                device.update(jsonState);
                System.out.println(System.currentTimeMillis() + ": >>> " + jsonState);
            } catch (AWSIotException e) {
                System.out.println(System.currentTimeMillis() + ": update failed for " + jsonState);
                continue;
            }

*/
/*            try {
                // Retrieve updated document from the shadow
                String shadowState = device.get();
                System.out.println(System.currentTimeMillis() + ": <<< " + shadowState);

                thing = objectMapper.readValue(shadowState, Thing.class);
            } catch (AWSIotException e) {
                System.out.println(System.currentTimeMillis() + ": get failed for " + jsonState);
                continue;
            }

*/            Thread.sleep(1000);
        }

    }




public static void setLed(String location, long value){

     System.out.println("ShadowEchoSample#setLed :" + location + " " + value); 
      if( location.equals("kitchen")){
        if( value == 0){ 
		pin1.low(); 
	}else pin1.high(); 
      }

      if( location.equals("office")){
        if( value == 0){ 
		pin2.low(); 
	}else pin2.high(); 
      }	

      if( location.equals("livingroom")){
        if( value == 0){ 
		pin3.low(); 
	}else pin3.high(); 
      }	


}



}
