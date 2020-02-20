

package com.amazonaws.services.iot.client.sample.pubSub;

import java.io.IOException;

import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTopic;
import com.amazonaws.services.iot.client.sample.shadowEcho.ShadowEchoSample;
import com.amazonaws.services.iot.client.sample.shadowEcho.Thing;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestTopicListener extends AWSIotTopic {
	   ObjectMapper objectMapper = new ObjectMapper();
       Thing thing = new Thing();

    public TestTopicListener(String topic, AWSIotQos qos) {
        super(topic, qos);
       objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void onMessage(AWSIotMessage message) {
        System.out.println(System.currentTimeMillis() + ": <<< payload: " + message.getStringPayload() + " topic: " + message.getTopic());
        try {
			thing = objectMapper.readValue(message.getStringPayload(), Thing.class);
			System.out.println("thing: " + thing);
			ShadowEchoSample.setLed("kitchen", thing.state.kitchen);	
			ShadowEchoSample.setLed("office", thing.state.office);	
			ShadowEchoSample.setLed("livingroom", thing.state.livingroom);	
				
		} catch (IOException e) {
			e.printStackTrace();
		} 
    }

}
