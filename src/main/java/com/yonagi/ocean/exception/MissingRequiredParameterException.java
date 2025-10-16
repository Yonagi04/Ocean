package com.yonagi.ocean.exception;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 11:04
 */
public class MissingRequiredParameterException extends Exception{
    public MissingRequiredParameterException(String message) {
        super(message);
    }
}
