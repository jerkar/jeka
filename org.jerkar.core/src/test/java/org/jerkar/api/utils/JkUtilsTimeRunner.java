package org.jerkar.api.utils;

public class JkUtilsTimeRunner {

    public static void main(String[] args) {
	final String pattern = "yyyyMMdd.HHmmss";
	System.out.println(JkUtilsTime.now(pattern));
	System.out.println(JkUtilsTime.nowUtc(pattern));
    }

}
