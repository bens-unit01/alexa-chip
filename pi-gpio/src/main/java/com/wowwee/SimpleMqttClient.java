package com.wowwee;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class SimpleMqttClient implements MqttCallback{

	public void connectionLost(Throwable t) {
	    System.out.println("SimpleMqttClient# connection lost ...error: ");
	    t.printStackTrace();
	   
	}

	public void deliveryComplete(IMqttDeliveryToken token) {
		try {
			//System.out.println("Pub complete" + new String(token.getMessage().getPayload()));
			System.out.println("SimpleMqttClient#deliveryComplete");
//		} catch (MqttException e) {
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}

	public void messageArrived(String topic, MqttMessage message) throws Exception {
		System.out.println("-------------------------------------------------");
		System.out.println("| Topic:" + topic);
		System.out.println("| Message: " + new String(message.getPayload()));
        System.out.println("-------------------------------------------------");	
	}

}
