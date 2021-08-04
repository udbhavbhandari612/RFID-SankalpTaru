package com.rr.hf.SankalpTaru.listeners;

import java.util.Map;

public class MifareData {

    private OnMifareDataListener listener;

    public MifareData() {
        this.listener = null;
    }

    public void setOnMifareDataListener(OnMifareDataListener listener) {
        this.listener = listener;

    }

    public void onDataRead(Map<String, String> map) {
        this.listener.onDataRead(map);
    }

    public void onDataWrite(boolean isSuccess) {
        this.listener.onDataWrite(isSuccess);
    }

    public void onDataDelete(boolean isSuccess) {
        this.listener.onDataDelete(isSuccess);
    }

    public void onError(String error) {
        this.listener.onError(error);
    }
}
