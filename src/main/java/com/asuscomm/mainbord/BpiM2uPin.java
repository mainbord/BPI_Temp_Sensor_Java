package com.asuscomm.mainbord;


import java.util.Map;
import java.util.TreeMap;

public class BpiM2uPin {

    public final static Map<String, String> pins = new TreeMap<>();

    static {
        pins.put("pin7","gpio35");
        pins.put("pin11","gpio276");
        pins.put("pin40","gpio229");
    }
}
