package com.dbay.teddy.manager;

import org.springframework.stereotype.Service;

/**
 * @author AlexanderGuo
 */


@Service
public class TokenManager {

    public static String getToken(){
        return "it is just a token";
    }

    public static void saveToken(String token){
        // Token persistence is not implemented by the current fixed-token authentication.
    }

    public static String createToken(){
        return "it is just a token";
    }

}
