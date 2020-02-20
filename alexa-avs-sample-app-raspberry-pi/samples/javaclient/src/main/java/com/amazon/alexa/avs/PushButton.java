package com.amazon.alexa.avs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PushButton {

	ActionListener listener; 
	boolean newAction = false; 
	public PushButton(ActionListener l) {
        listener = l;
        
   
	}

	   
	   
   public void actionOccurred(){
	   new Thread(new Runnable() {
		
		@Override
		public void run() {
	       listener.actionPerformed(new ActionEvent(new Object(), 0, "command"));   
		}
	}).start(); 
   }	
	
}
