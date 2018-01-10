package com.example.box.util;


import com.example.box.gson.Home;
import com.example.box.gson.HomeAlarm;
import com.example.box.gson.HomeInfo;
import com.google.gson.Gson;

/**
 * Created by Administrator on 2018/1/8.
 */

public class Util {

    public static Home handleHomeInfo(String response){

        Gson gson = new Gson();
        Home home = gson.fromJson(response,Home.class);
        return home;
    }



}
