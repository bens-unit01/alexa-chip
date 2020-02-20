package com.wowwee;


import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.GpioPinPwm;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.wiringpi.SoftPwm;

public class MotorControl {


	static final int PWM_PIN = 8;

	public MotorControl() {
		final GpioController gpio = GpioFactory.getInstance();
		final GpioPinDigitalOutput pin1 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_16, "PinLED", PinState.HIGH);
		final GpioPinDigitalOutput pin2 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, "PinLED", PinState.HIGH);
		final GpioPinDigitalOutput pin3 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, "PinLED", PinState.LOW);
		final GpioPinDigitalOutput pin4 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_15, "PinLED", PinState.HIGH);
		
		SoftPwm.softPwmCreate(PWM_PIN, 0, 100);

	}


	public void turnMotorOn(){

		SoftPwm.softPwmWrite(PWM_PIN, 60);
	}	

	public void turnMotorOff(){
		SoftPwm.softPwmWrite(PWM_PIN, 0);
	}
	public static void test01() {


		MotorControl m = new MotorControl();
             for(int i =0; i< 5; i++){
		System.out.println("motor on ");
		m.turnMotorOn();
		try {
			Thread.sleep(3000);
			System.out.println();
			System.out.println("motor off ");
			Thread.sleep(1000);
			m.turnMotorOff(); 
		} catch (InterruptedException e) {
			e.printStackTrace();
		}			
	 }

	}

}


