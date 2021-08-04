package com.rr.hf.SankalpTaru.utils;

import com.rr.hf.SankalpTaru.listeners.MifareData;
import com.rr.hf.oem09operations.DeviceManager.BleDevice;
import com.rr.hf.oem09operations.DeviceManager.ComByteManager;
import com.rr.hf.oem09operations.DeviceManager.DeviceManager;
import com.rr.hf.oem09operations.Exception.CardNoResponseException;
import com.rr.hf.oem09operations.Exception.DeviceNoResponseException;
import com.rr.hf.oem09operations.card.Mifare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OEMHelper {

    private static final int[] invalidBlocks = new int[]{0, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63};

    public static void readMifareCard(BleDevice sBleDevice, MifareData mfData) {
        final int[] blocks = new int[]{1, 2, 4, 5, 6, 8, 9, 10, 12, 13, 14, 16, 17, 18, 20, 21};
        final Map<String, String> data = new HashMap<>();
        sBleDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, (blnIsSus, cardType, bytCardSn, bytCarATS) -> new Thread(() -> {
            try {
                boolean isFound = startAutoSearchCard(sBleDevice);
                if (isFound) {
                    sBleDevice.stoptAutoSearchCard();
                    if (cardType == DeviceManager.CARD_TYPE_MIFARE) {
                        final Mifare mifare = (Mifare) sBleDevice.getCard();
                        //card is matched, so, go with inventory operation.
                        if (mifare != null) {
                            try {
                                for (int block : blocks) {
                                    byte[] key = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
                                    boolean auth = mifare.authenticate((byte) block, Mifare.MIFARE_KEY_TYPE_B, key);
                                    if (auth) {
                                        byte[] bytes = mifare.read((byte) block);
                                        StringBuilder value = new StringBuilder();
                                        for (byte aByte : bytes) {
                                            value.append((char) aByte);
                                        }
                                        String prev = data.get(OEMHelper.getAccessor(block));
                                        if (prev != null)
                                            data.put(OEMHelper.getAccessor(block), prev + value.toString());
                                        else
                                            data.put(OEMHelper.getAccessor(block), value.toString());
                                    } else {
                                        data.clear();
                                        mfData.onError("Error (Dead Block) : Authentication failed for block " + block + "\n");
                                        break;
                                    }
                                }
                                //success read
                                mfData.onDataRead(data);

                            } catch (CardNoResponseException ex) {
                                mfData.onError("Error : Card is not responding or another problem.." + "\n");
                            }
                        }
                    } else {
                        mfData.onError("Error : Card not found" + "\n");
                    }
                }
                sBleDevice.closeRf();
            } catch (DeviceNoResponseException de) {
                mfData.onError("Error : Device not responding" + "\n");
            }
        }).start());
    }

    public static void deleteDataMifareCard(BleDevice sBleDevice, MifareData mfData, List<Integer> blocks) {

        sBleDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, (blnIsSus, cardType, bytCardSn, bytCarATS) -> new Thread(() -> {
            try {
                boolean isFound = startAutoSearchCard(sBleDevice);
                if (isFound) {
                    sBleDevice.stoptAutoSearchCard();
                    if (cardType == DeviceManager.CARD_TYPE_MIFARE) {
                        final Mifare mifare = (Mifare) sBleDevice.getCard();
                        //card is matched, so, go with inventory operation.
                        if (mifare != null) {
                            try {
                                int count = 0;
                                for (int block : blocks) {
                                    byte[] key = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
                                    boolean auth = mifare.authenticate((byte) block, Mifare.MIFARE_KEY_TYPE_B, key);
                                    if (auth) {
                                        boolean isDeleted = mifare.write((byte) block, StringHelper.stringToBytes("", 16));
                                        if (isDeleted)
                                            count++;
                                    } else {
                                        mfData.onError("Error (Dead Block) : Authentication failed for block " + block + "\n");
                                        break;
                                    }
                                }
                                if (count == blocks.size())
                                    mfData.onDataDelete(true);
                                else
                                    mfData.onError("Error deleting some values");
                            } catch (CardNoResponseException ex) {
                                mfData.onError("Error : Card is not responding or another problem.." + "\n");
                            }
                        }
                    } else {
                        mfData.onError("Error : Card not found" + "\n");
                    }
                }
                sBleDevice.closeRf();
            } catch (DeviceNoResponseException de) {
                mfData.onError("Error : Device not responding" + "\n");
            }
        }).start());
    }

    public static void writeMifareCard(final Map<String, byte[]> map, final BleDevice sBleDevice, MifareData mfData) {
        final StringBuilder error = new StringBuilder();
        sBleDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, (blnIsSus, cardType, bytCardSn, bytCarATS) -> new Thread(() -> {
            try {
                boolean isFound = startAutoSearchCard(sBleDevice);
                if (isFound) {
                    sBleDevice.stoptAutoSearchCard();
                    if (cardType == DeviceManager.CARD_TYPE_MIFARE) {
                        final Mifare mifare = (Mifare) sBleDevice.getCard();
                        //card is matched, so, go with inventory operation.
                        if (mifare != null) {
                            try {
                                for (Map.Entry<String, byte[]> entry : map.entrySet()) {
                                    int blockToWrite = Integer.parseInt(entry.getKey());
                                    if (Arrays.binarySearch(invalidBlocks, blockToWrite) < 0) {
                                        byte[] data = entry.getValue();
                                        byte[] key = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
                                        boolean auth = mifare.authenticate((byte) blockToWrite, Mifare.MIFARE_KEY_TYPE_B, key);
                                        if (auth) {
                                            mifare.write((byte) blockToWrite, data);
                                        } else {
                                            error.append("Authentication failure for block ").append(blockToWrite).append("\n");
                                            mfData.onError(error.toString());
                                        }
                                    } else {
                                        error.append("Invalid block").append(blockToWrite).append("\n");
                                        mfData.onError(error.toString());
                                    }
                                }
                                if (error.length() == 0)
                                    mfData.onDataWrite(true);
                            } catch (CardNoResponseException ex) {
                                error.append("CardNoResponse : ").append(ex.getMessage()).append("\n");
                                mfData.onError(error.toString());
                            }
                        }
                        sBleDevice.closeRf();
                    } else {
                        error.append("Card not available").append("\n");
                        mfData.onError(error.toString());
                    }
                }
            } catch (DeviceNoResponseException de) {
                error.append("DeviceNoResponse : ").append(de.getMessage());
            }
        }).start());

    }

    public static String getAccessor(int block) {
        String accessor = "";
        switch (block) {
            case 1:
                accessor = "latitude";
                break;
            case 2:
                accessor = "longitude";
                break;
            case 4:
                accessor = "tree_id";
                break;
            case 5:
                accessor = "tree_name";
                break;
            case 6:
                accessor = "species";
                break;
            case 8:
                accessor = "plantation_date";
                break;
            case 9:
            case 10:
            case 12:
            case 13:
            case 14:
            case 16:
            case 17:
            case 18:
                accessor = "tree_url";
                break;
            case 20:
            case 21:
                accessor = "beneficiary_name";
                break;
        }
        return accessor;
    }

    public static List<Integer> getBlocksFromAccessor(String accessor) {
        List<Integer> blocks = new ArrayList<>();
        switch (accessor) {
            case "latitude":
            case "longitude":
                blocks.add(1);
                blocks.add(2);
                break;
            case "tree_id":
                blocks.add(4);
                break;
            case "tree_name":
                blocks.add(5);
                break;
            case "species":
                blocks.add(6);
                break;
            case "plantation_date":
                blocks.add(8);
                break;
            case "tree_url":
                blocks.add(9);
                blocks.add(10);
                blocks.add(12);
                blocks.add(13);
                blocks.add(14);
                blocks.add(16);
                blocks.add(17);
                blocks.add(18);
                break;
            case "beneficiary_name":
                blocks.add(20);
                blocks.add(21);
                break;
            default:
                blocks.add(62);
        }
        return blocks;
    }

    public static String getNameFromAccessor(String accessor) {
        switch (accessor) {
            case "latitude":
                return "Latitude";
            case "longitude":
                return "Longitude";
            case "tree_id":
                return "Tree ID";
            case "tree_name":
                return "Tree Name";
            case "species":
                return "Species";
            case "plantation_date":
                return "Plantation Date\n(dd/mm/yyyy)";
            case "tree_url":
                return "Tree URL";
            case "beneficiary_name":
                return "Beneficiary Name";
            default:
                return "";
        }
    }

    private static boolean startAutoSearchCard(BleDevice sBleDevice) throws DeviceNoResponseException {
        //open auto search, interval 20ms
        boolean isSuc;
        int falseCnt = 0;
        do {
            isSuc = sBleDevice.startAutoSearchCard((byte) 20, ComByteManager.ISO14443_P4);
        } while (!isSuc && (falseCnt++ < 1000));
        return isSuc;
    }

}