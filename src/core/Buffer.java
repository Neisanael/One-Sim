/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.util.*;

/**
 *
 * @author Asus
 */
public class Buffer {
    
    public static final String B_SIZE_S = "bufferSize";
    
    private int bufferSize;
    
    public int getBufferSize(DTNHost host){
        bufferSize = Integer.MAX_VALUE;
        
        Settings s = new Settings();
        
        if (s.contains(B_SIZE_S)){
            bufferSize = s.getInt(B_SIZE_S);
        }
        
        return bufferSize;
    }
    
    public Iterator<Message> iterator(){
        List<Message> messages = new ArrayList<>();
        return messages.iterator();
    }
    
}