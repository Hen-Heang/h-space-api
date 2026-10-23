package com.example.jwt_authentication.exception;


public class CusNotFoundException extends RuntimeException{

    public CusNotFoundException(String msg, Throwable t){
        super(msg,t);
    }

    public CusNotFoundException(String msg){
        super(msg);
    }

    public CusNotFoundException(){
        super();
    }
}
