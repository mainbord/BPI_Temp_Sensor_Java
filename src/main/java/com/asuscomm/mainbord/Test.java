package com.asuscomm.mainbord;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class Test {

    public static void main(String[] args) {
/*        double sum = 0;
        for (int i = 0; i < 201; i++) {
            System.out.println(sum + ": " + Integer.toBinaryString(i));
            sum = sum + 0.625;
        }*/

        System.out.println(stringCompleteTo12(Integer.toBinaryString(1)));
        System.out.println(Integer.parseInt("1111111111111111", 2));

        Map<Double, String> tempCelcFromBinary = new TreeMap<>();

        double sum = -55;
        for (int i = 64656; i <= 65535; i++) {
            tempCelcFromBinary.put(sum, stringCompleteTo12(Integer.toBinaryString(i)));
            sum = sum + 0.0625;
        }
        System.out.println(tempCelcFromBinary);
        System.out.println("_-_____________________________");
        System.out.println(tempCelcFromBinary.get(-25.0625).equals("1111111001101111"));
    }

    public static String stringCompleteTo12(String in) {
        StringBuilder sb = new StringBuilder(in);
        for (int i = 0; i < 12 - in.length(); i++) {
            sb.insert(0,"0");
        }
        return sb.toString();
    }
}
