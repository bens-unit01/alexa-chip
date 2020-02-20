package com.amazon.alexa.avs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.PinEdge; 


public class TeddyPushButton {

	/**
	 * 	 * 
	 * 	 	 */
	final GpioController gpio = GpioFactory.getInstance(); 
	final GpioPinDigitalOutput pin2 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_03, "PinLED", PinState.HIGH);
	final GpioPinDigitalInput myButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02, PinPullResistance.PULL_DOWN);
	private static final long serialVersionUID = 1L;

	public TeddyPushButton(ActionListener l) {

		myButton.addListener(new GpioPinListenerDigital() {

			public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
				if(event.getEdge() == PinEdge.RISING){
					System.out.println("push button" + event.getEdge() + " " + event.getState() );
					l.actionPerformed(new ActionEvent(new Object(), 0, "command"));
				}
			}
		});

	}




}


