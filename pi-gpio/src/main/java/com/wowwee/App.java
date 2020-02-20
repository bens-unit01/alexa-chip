package com.wowwee;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.GpioPinPwm;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.wiringpi.Gpio;
import com.pi4j.wiringpi.SoftPwm;
import com.pi4j.io.gpio.PinEdge; 

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence; 


public class App 
{



	@SuppressWarnings("unused")
	public static void main( String[] args )
	{

                MotorControl.test01(); 		
		while(true); 


	}

	private static void pwmTest(){
		final GpioController gpio = GpioFactory.getInstance();
		final GpioPinDigitalOutput pin1 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "PinLED", PinState.HIGH);
		final GpioPinDigitalOutput pin2 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, "PinLED", PinState.HIGH);
		final GpioPinDigitalOutput pin3 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, "PinLED", PinState.LOW);
		final GpioPinDigitalOutput pin4 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_15, "PinLED", PinState.HIGH);
		final GpioPinDigitalInput myButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02, PinPullResistance.PULL_DOWN);

		//Gpio.wiringPiSetup(); 
		SoftPwm.softPwmCreate(26, 0, 100); 
		SoftPwm.softPwmWrite(26, 40);
                System.out.println("pwm test ...");  

	}

	private static void ledTest(){
		final GpioController gpio = GpioFactory.getInstance();
		System.out.println("light is: ON");
		final GpioPinDigitalOutput pin1 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "PinLED", PinState.HIGH);
		final GpioPinDigitalOutput pin2 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, "PinLED", PinState.HIGH);
		final GpioPinDigitalOutput pin3 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, "PinLED", PinState.HIGH);
		final GpioPinDigitalInput myButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02, PinPullResistance.PULL_DOWN);

		try{
			Thread.sleep(2000);

			pin1.low();
			pin2.low();
			pin3.low();
			System.out.println("light is: OFF");
			Thread.sleep(1000);
			System.out.println("light is: ON for 1 second");
			pin1.pulse(1000, true);
			pin2.pulse(1000, true);
			pin3.pulse(1000, true);


			myButton.addListener(new GpioPinListenerDigital() {

				public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
					if(event.getEdge() == PinEdge.RISING){
						System.out.println("push button" + event.getEdge() + " " + event.getState() );	
					}
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			});
		} catch(InterruptedException ex){
			System.out.println("Exception ...."); 
		}



	}
	private static void mqttTest(){
		String topic        = "MQTT Examples";
		String content      = "Message from MqttPublishSample";
		int qos             = 2;
		String broker       = "tcp://10.10.10.31:1883";
		String clientId     = "JavaSample";

		System.out.println("app App.java");
		MemoryPersistence persistence = new MemoryPersistence(); 
		try {
			MqttClient sampleClient = new MqttClient(broker, clientId, persistence);
			MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setCleanSession(true);
			sampleClient.setCallback(new SimpleMqttClient());
			sampleClient.connect(connOpts);
			sampleClient.subscribe("kitchen");
			MqttMessage message = new MqttMessage(content.getBytes());
			message.setQos(qos);
			sampleClient.publish(topic, message);
			while(true); 
		} catch(MqttException me) {
			System.out.println("reason "+me.getReasonCode());
			System.out.println("msg "+me.getMessage());
			System.out.println("loc "+me.getLocalizedMessage());
			System.out.println("cause "+me.getCause());
			System.out.println("excep "+me);
			me.printStackTrace();
		}	


	}


}

