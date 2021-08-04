package com.rr.hf.SankalpTaru.listeners;

import java.util.Map;

public interface OnMifareDataListener {
    void onDataRead(Map<String, String> data);
    void onDataWrite(boolean isSuccess);
    void onDataDelete(boolean isSuccess);

    void onError(String error);
}
