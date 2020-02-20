
package com.amazonaws.services.iot.client.sample.shadowEcho;

/**
 * This POJO class defines a simple document for communicating with the AWS IoT
 * thing. It only contains one property ({@code counter}).
 */
public class Thing {

    public State state = new State();

    public static class State {
    	public int office; 
    	public int kitchen; 
    	public int livingroom; 
    }

}
