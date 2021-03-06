package com.aliyun.alink.devicesdk.demo;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.alink.apiclient.utils.StringUtils;
import com.aliyun.alink.dm.api.BaseInfo;
import com.aliyun.alink.linkkit.api.LinkKit;
import com.aliyun.alink.linksdk.cmp.connect.channel.MqttPublishRequest;
import com.aliyun.alink.linksdk.cmp.core.base.AMessage;
import com.aliyun.alink.linksdk.cmp.core.base.ARequest;
import com.aliyun.alink.linksdk.cmp.core.base.AResponse;
import com.aliyun.alink.linksdk.cmp.core.base.ConnectState;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectNotifyListener;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectSendListener;
import com.aliyun.alink.linksdk.tmp.api.InputParams;
import com.aliyun.alink.linksdk.tmp.api.OutputParams;
import com.aliyun.alink.linksdk.tmp.device.payload.ValueWrapper;
import com.aliyun.alink.linksdk.tmp.devicemodel.Arg;
import com.aliyun.alink.linksdk.tmp.devicemodel.Event;
import com.aliyun.alink.linksdk.tmp.devicemodel.Property;
import com.aliyun.alink.linksdk.tmp.devicemodel.Service;
import com.aliyun.alink.linksdk.tmp.listener.IPublishResourceListener;
import com.aliyun.alink.linksdk.tmp.listener.ITResRequestHandler;
import com.aliyun.alink.linksdk.tmp.listener.ITResResponseCallback;
import com.aliyun.alink.linksdk.tmp.utils.ErrorInfo;
import com.aliyun.alink.linksdk.tmp.utils.GsonUtils;
import com.aliyun.alink.linksdk.tmp.utils.TmpConstant;
import com.aliyun.alink.linksdk.tools.AError;
import com.aliyun.alink.linksdk.tools.ALog;
import com.aliyun.alink.linksdk.tools.TextUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ThingSample extends BaseSample {
    private static final String TAG = "ThingSample";

    private final static String SERVICE_SET = "set";
    private final static String SERVICE_GET = "get";
    private final static String CONNECT_ID = "LINK_PERSISTENT";
    final static Pattern pattern = Pattern.compile("^[-\\+]?[.\\d]*$");

    private final static int DEF_VALUE = Integer.MIN_VALUE;

    private String identity = null;
    private String value = null;
    private ValueWrapper valueWrapper = null;
    private HashMap<String, ValueWrapper> valueWrapperMap = null;
    private ThingData mThingData = null;
    private Gson mGson = new Gson();

    private boolean isEvent = false;

    public ThingSample(String pk, String dn) {
        super(pk, dn);
    }

    public void readData(String path) {
        String data = FileUtils.readFile(path);
        mThingData = mGson.fromJson(data, ThingData.class);
        if (mThingData == null) {
            ALog.e(TAG, "??????????????????");
            return;
        }
        identity = mThingData.identifier;
        value = mThingData.value;
        if ("event".equals(mThingData.type)) {
            isEvent = true;
            getPostEvent();
        } else {
            isEvent = false;
            getPost(identity, value);
        }
    }

    private void getPost(String identity, String value) {
        try {
            if (StringUtils.isEmptyString(identity)) {
                ALog.w(TAG, "????????????");
                return;
            }
            List<Property> propertyList = LinkKit.getInstance().getDeviceThing().getProperties();
            if (propertyList == null) {
                ALog.w(TAG, "???????????????property????????????");
                return;
            }
            Property property = null;
            for (int i = 0; i < propertyList.size(); i++) {
                property = propertyList.get(i);
                if (property == null) {
                    continue;
                }
                if (identity.equals(property.getIdentifier())) {
                    break;
                }
                property = null;
            }
            if (property == null) {
                ALog.w(TAG, "???????????????");
                return;
            }
            if (TmpConstant.TYPE_VALUE_INTEGER.equals(property.getDataType().getType())) {
                int parseData = getInt(value);
                if (parseData != DEF_VALUE) {
                    updateCache(property.getIdentifier(), new ValueWrapper.IntValueWrapper(parseData));
                } else {
                    ALog.w(TAG, "??????????????????");
                }
                return;
            }
            if (TmpConstant.TYPE_VALUE_FLOAT.equals(property.getDataType().getType())) {
                Double parseData = getDouble(value);
                if (parseData != null) {
                    updateCache(property.getIdentifier(), new ValueWrapper.DoubleValueWrapper(parseData));
                } else {
                    ALog.w(TAG, "??????????????????");
                }
                return;
            }
            if (TmpConstant.TYPE_VALUE_DOUBLE.equals(property.getDataType().getType())) {
                Double parseData = getDouble(value);
                if (parseData != null) {
                    updateCache(property.getIdentifier(), new ValueWrapper.DoubleValueWrapper(parseData));
                } else {
                    ALog.w(TAG, "??????????????????");
                }
                return;
            }
            if (TmpConstant.TYPE_VALUE_BOOLEAN.equals(property.getDataType().getType())) {
                int parseData = getInt(value);
                if (parseData == 0 || parseData == 1) {
                    updateCache(property.getIdentifier(), new ValueWrapper.BooleanValueWrapper(parseData));
                } else {
                    ALog.w(TAG, "??????????????????");
                }
                return;
            }
            if (TmpConstant.TYPE_VALUE_TEXT.equals(property.getDataType().getType())) {
                updateCache(property.getIdentifier(), new ValueWrapper.StringValueWrapper(value));
                return;
            }
            if (TmpConstant.TYPE_VALUE_DATE.equals(property.getDataType().getType())) {
                updateCache(property.getIdentifier(), new ValueWrapper.DateValueWrapper(value));
                return;
            }
            if (TmpConstant.TYPE_VALUE_ENUM.equalsIgnoreCase(property.getDataType().getType())) {
                updateCache(property.getIdentifier(), new ValueWrapper.EnumValueWrapper(getInt(value)));
                return;
            }
            if (TmpConstant.TYPE_VALUE_ARRAY.equalsIgnoreCase(property.getDataType().getType())) {
                ValueWrapper.ArrayValueWrapper arrayValueWrapper = GsonUtils.fromJson(value, new TypeToken<ValueWrapper>() {
                }.getType());
                updateCache(property.getIdentifier(), arrayValueWrapper);
                return;
            }
            // ?????????????????????  ??????????????????????????????????????????
            if (TmpConstant.TYPE_VALUE_STRUCT.equals(property.getDataType().getType())) {
                try {
                    List<Map<String, Object>> specsList = (List<Map<String, Object>>) property.getDataType().getSpecs();
                    if (specsList == null || specsList.size() == 0) {
                        ALog.w(TAG, "???????????????struct????????????????????????????????????");
                        return;
                    }
                    Gson gson = new Gson();
                    JsonObject dataJson = gson.fromJson(value, JsonObject.class);
                    Map<String, ValueWrapper> dataMap = new HashMap<String, ValueWrapper>();
                    Map<String, Object> specsItem = null;
                    for (int i = 0; i < specsList.size(); i++) {
                        specsItem = specsList.get(i);
                        if (specsItem == null) {
                            continue;
                        }
                        String idKey = (String) specsItem.get("identifier");
                        String dataType = (String) ((Map) specsItem.get("dataType")).get("type");
                        if (idKey != null && dataJson.has(idKey) && dataType != null) {
                            ValueWrapper valueItem = null;
                            if ("int".equals(dataType)) {
                                valueItem = new ValueWrapper.IntValueWrapper(getInt(String.valueOf(dataJson.get(idKey))));
                            } else if ("text".equals(dataType)) {
                                valueItem = new ValueWrapper.StringValueWrapper(dataJson.get(idKey).getAsString());
                            } else if ("float".equals(dataType) || "double".equals(dataType)) {
                                valueItem = new ValueWrapper.DoubleValueWrapper(getDouble(String.valueOf(dataJson.get(idKey))));
                            } else if ("bool".equals(dataType)) {
                                valueItem = new ValueWrapper.BooleanValueWrapper(getInt(String.valueOf(dataJson.get(idKey))));
                            } else if ("date".equals(dataType)) {
                                if (isValidInt(String.valueOf(dataJson.get(idKey)))) {
                                    valueItem = new ValueWrapper.DateValueWrapper(String.valueOf(dataJson.get(idKey)));
                                } else {
                                    ALog.w(TAG, "??????????????????");
                                }
                            } else if ("enum".equals(dataType)) {
                                valueItem = new ValueWrapper.EnumValueWrapper(getInt(String.valueOf(dataJson.get(idKey))));
                            } else {
                                ALog.w(TAG, "?????????????????????");
                            }
                            if (valueItem != null) {
                                dataMap.put(idKey, valueItem);
                            }
                        }
                    }

                    updateCache(property.getIdentifier(), new ValueWrapper.StructValueWrapper(dataMap));
                } catch (Exception e) {
                    ALog.e(TAG, "?????????????????????");
                }
                return;
            }
            ALog.w(TAG, "?????????Demo?????????????????????????????????????????????????????????????????????");
        } catch (Exception e) {
            ALog.e(TAG, "??????????????????");
            e.printStackTrace();
        }
    }

    private void getPostEvent() {
        if (StringUtils.isEmptyString(identity)) {
            ALog.w(TAG, "??????identifier??????");
            return;
        }
        List<Event> propertyList = LinkKit.getInstance().getDeviceThing().getEvents();
        if (propertyList == null) {
            ALog.w(TAG, "??????????????? event????????????");
            return;
        }
        Event event = null;
        for (int i = 0; i < propertyList.size(); i++) {
            event = propertyList.get(i);
            if (event == null) {
                continue;
            }
            if (identity.equals(event.getIdentifier())) {
                break;
            }
            event = null;
        }
        if (event == null) {
            ALog.w(TAG, "???????????????");
            return;
        }

        HashMap<String, ValueWrapper> hashMap = new HashMap<String, ValueWrapper>();
        try {
            JSONObject object = JSONObject.parseObject(value);
            if (object == null) {
                ALog.d(TAG, "??????????????????");
                return;
            }
            if (event.getOutputData() != null) {
                for (int i = 0; i < event.getOutputData().size(); i++) {
                    Arg arg = event.getOutputData().get(i);
                    if (arg == null || arg.getDataType() == null || arg.getIdentifier() == null) {
                        continue;
                    }
                    String idnValue = String.valueOf(object.get(arg.getIdentifier()));
                    if (idnValue == null || object.get(arg.getIdentifier()) == null) {
                        continue;
                    }
                    if (TmpConstant.TYPE_VALUE_INTEGER.equals(arg.getDataType().getType())) {
                        int parseData = getInt(idnValue);
                        if (parseData != DEF_VALUE) {
                            hashMap.put(arg.getIdentifier(), new ValueWrapper.IntValueWrapper(parseData));
                        } else {
                            ALog.d(TAG, "??????????????????");
                            break;
                        }
                        continue;
                    }
                    if (TmpConstant.TYPE_VALUE_FLOAT.equals(arg.getDataType().getType())) {
                        Double parseData = getDouble(idnValue);
                        if (parseData != null) {
                            hashMap.put(arg.getIdentifier(), new ValueWrapper.DoubleValueWrapper(parseData));
                        } else {
                            ALog.d(TAG, "??????????????????");
                            break;
                        }
                        continue;
                    }
                    if (TmpConstant.TYPE_VALUE_DOUBLE.equals(arg.getDataType().getType())) {
                        Double parseData = getDouble(idnValue);
                        if (parseData != null) {
                            hashMap.put(arg.getIdentifier(), new ValueWrapper.DoubleValueWrapper(parseData));
                        } else {
                            ALog.d(TAG, "??????????????????");
                            break;
                        }
                        continue;
                    }
                    if (TmpConstant.TYPE_VALUE_BOOLEAN.equals(arg.getDataType().getType())) {
                        int parseData = getInt(idnValue);
                        if (parseData == 0 || parseData == 1) {
                            hashMap.put(arg.getIdentifier(), new ValueWrapper.BooleanValueWrapper(parseData));
                        } else {
                            ALog.d(TAG, "??????????????????");
                            break;
                        }
                        continue;
                    }
                    if (TmpConstant.TYPE_VALUE_TEXT.equals(arg.getDataType().getType())) {
                        hashMap.put(arg.getIdentifier(), new ValueWrapper.StringValueWrapper(idnValue));
                        continue;
                    }
                    if (TmpConstant.TYPE_VALUE_DATE.equals(arg.getDataType().getType())) {
                        hashMap.put(arg.getIdentifier(), new ValueWrapper.DateValueWrapper(idnValue));
                        continue;
                    }
                    if (TmpConstant.TYPE_VALUE_ENUM.equalsIgnoreCase(arg.getDataType().getType())) {
                        hashMap.put(arg.getIdentifier(), new ValueWrapper.EnumValueWrapper(getInt(idnValue)));
                        continue;
                    }
                    if (TmpConstant.TYPE_VALUE_ARRAY.equalsIgnoreCase(arg.getDataType().getType())) {
                        ValueWrapper.ArrayValueWrapper arrayValueWrapper = GsonUtils.fromJson(idnValue, new TypeToken<ValueWrapper>() {
                        }.getType());
                        hashMap.put(arg.getIdentifier(), arrayValueWrapper);
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ALog.w(TAG, "??????????????????");
            return;
        }
        valueWrapperMap = hashMap;
    }

    private void reportEvent() {
        OutputParams params = new OutputParams(valueWrapperMap);
        LinkKit.getInstance().getDeviceThing().thingEventPost(identity, params, new IPublishResourceListener() {
            public void onSuccess(String resId, Object o) {
                // ??????????????????
                ALog.d(TAG, "onSuccess() called with: s = [" + resId + "], o = [" + o + "]");
            }

            public void onError(String resId, AError aError) {
                // ??????????????????
                ALog.w(TAG, "onError() called with: s = [" + resId + "], aError = [" + getError(aError) + "]");
            }
        });
    }

    /**
     * ??????????????????
     */
    private void updateCache(String identifier, ValueWrapper valueWrapper) {
        identity = identifier;
        this.valueWrapper = valueWrapper;
    }

    public void report() {
        if (isEvent) {
            ALog.d(TAG, "????????????" + identity);
            reportEvent();
        } else {
            ALog.d(TAG, "????????????" + identity);
            reportProperty();
        }
    }

    private void reportProperty(){
        if (StringUtils.isEmptyString(identity) || valueWrapper == null) {
            ALog.e(TAG, "??????????????????");
            return;
        }

        ALog.d(TAG, "?????? ??????identity=" + identity);

        Map<String, ValueWrapper> reportData = new HashMap<String, ValueWrapper>();
        reportData.put(identity, valueWrapper);
        LinkKit.getInstance().getDeviceThing().thingPropertyPost(reportData, new IPublishResourceListener() {

            public void onSuccess(String s, Object o) {
                // ??????????????????
                ALog.d(TAG, "???????????? onSuccess() called with: s = [" + s + "], o = [" + o + "]");
            }

            public void onError(String s, AError aError) {
                // ??????????????????
                ALog.d(TAG, "????????????onError() called with: s = [" + s + "], aError = [" + getError(aError) + "]");
            }
        });
    }
    /**
     * ???????????????????????????????????????????????????????????????????????????????????????
     * ???????????????????????????????????????????????????????????????????????????????????????
     * ???????????????????????????????????? Error ?????????
     */
    public void setServiceHandler() {
        ALog.d(TAG, "setServiceHandler() called");
        List<Service> srviceList = LinkKit.getInstance().getDeviceThing().getServices();
        for (int i = 0; srviceList != null && i < srviceList.size(); i++) {
            Service service = srviceList.get(i);
            LinkKit.getInstance().getDeviceThing().setServiceHandler(service.getIdentifier(), mCommonHandler);
        }
        LinkKit.getInstance().registerOnNotifyListener(connectNotifyListener);
        //
    }

    private String printAMessage(AMessage aMessage) {
        return (aMessage == null || aMessage.data == null) ? "" : new String((byte[]) aMessage.data);
    }

    private IConnectNotifyListener connectNotifyListener = new IConnectNotifyListener() {
        public void onNotify(String connectId, String topic, AMessage aMessage) {
            ALog.d(TAG, "onNotify() called with: connectId = [" + connectId + "], topic = [" + topic + "], aMessage = [" + printAMessage(aMessage) + "]");
            try {
                if (CONNECT_ID.equals(connectId) && !StringUtils.isEmptyString(topic) &&
                        topic.startsWith("/sys/" + productKey + "/" + deviceName + "/rrpc/request")) {
                    ALog.d(TAG, "??????????????????RRPC??????" + printAMessage(aMessage));
    //                    ALog.d(TAG, "receice Message=" + new String((byte[]) aMessage.data));
                    // ???????????????????????????  {"method":"thing.service.test_service","id":"123374967","params":{"vv":60},"version":"1.0.0"}
                    MqttPublishRequest request = new MqttPublishRequest();
                    request.isRPC = false;
                    request.topic = topic.replace("request", "response");
                    String resId = topic.substring(topic.indexOf("rrpc/request/") + 13);
                    request.msgId = resId;
                    // TODO ?????????????????????????????? ????????????
                    request.payloadObj = "{\"id\":\"" + resId + "\", \"code\":\"200\"" + ",\"data\":{} }";
    //                    aResponse.data =
                    LinkKit.getInstance().getMqttClient().publish(request, new IConnectSendListener() {
                        public void onResponse(ARequest aRequest, AResponse aResponse) {
                            ALog.d(TAG, "onResponse() called with: aRequest = [" + aRequest + "], aResponse = [" + aResponse + "]");
                        }

                        public void onFailure(ARequest aRequest, AError aError) {
                            ALog.d(TAG, "onFailure() called with: aRequest = [" + aRequest + "], aError = [" + getError(aError) + "]");
                        }
                    });
                }
                else if (CONNECT_ID.equals(connectId) && !TextUtils.isEmpty(topic) &&
                        topic.startsWith("/ext/rrpc/")) {
                    ALog.d(TAG, "?????????????????????RRPC??????");
    //                    ALog.d(TAG, "receice Message=" + new String((byte[]) aMessage.data));
                    // ???????????????????????????  {"method":"thing.service.test_service","id":"123374967","params":{"vv":60},"version":"1.0.0"}
                    MqttPublishRequest request = new MqttPublishRequest();
                    // ?????? 0 ??? 1??? ??????0
    //                request.qos = 0;
                    request.isRPC = false;
                    request.topic = topic.replace("request", "response");
                    String[] array = topic.split("/");
                    String resId = array[3];
                    request.msgId = resId;
                    // TODO ?????????????????????????????? ????????????
                    request.payloadObj = "{\"id\":\"" + resId + "\", \"code\":\"200\"" + ",\"data\":{} }";
    //                    aResponse.data =
                    LinkKit.getInstance().publish(request, new IConnectSendListener() {
                        @Override
                        public void onResponse(ARequest aRequest, AResponse aResponse) {
                            ALog.d(TAG, "onResponse() called with: aRequest = [" + aRequest + "], aResponse = [" + aResponse + "]");
                        }

                        @Override
                        public void onFailure(ARequest aRequest, AError aError) {
                            ALog.d(TAG, "onFailure() called with: aRequest = [" + aRequest + "], aError = [" + aError + "]");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public boolean shouldHandle(String s, String s1) {
            return true;
        }

        public void onConnectStateChange(String s, ConnectState connectState) {

        }
    };

    private ITResRequestHandler mCommonHandler = new ITResRequestHandler() {
        public void onProcess(String identify, Object result, ITResResponseCallback itResResponseCallback) {
            ALog.d(TAG, "onProcess() called with: s = [" + identify + "], o = [" + result + "], itResResponseCallback = [" + itResResponseCallback + "]");
            ALog.d(TAG, "?????????????????????????????? " + identify);
            try {
                if (SERVICE_SET.equals(identify)) {
                    // TODO  ???????????????????????????????????????  ?????????????????????
                    // ??????????????????????????????????????????????????????????????????
                    // ?????????????????????????????????????????????????????? ??????????????????????????????
                    boolean isSetPropertySuccess = true;
                    if (isSetPropertySuccess) {
                        if (result instanceof InputParams) {
                            Map<String, ValueWrapper> data = (Map<String, ValueWrapper>) ((InputParams) result).getData();
//                        data.get()
                            ALog.d(TAG, "???????????????????????? " + data);
                            // ???????????? ??????????????????
                            itResResponseCallback.onComplete(identify, null, null);
                        } else {
                            itResResponseCallback.onComplete(identify, null, null);
                        }
                    } else {
                        AError error = new AError();
                        error.setCode(100);
                        error.setMsg("setPropertyFailed.");
                        itResResponseCallback.onComplete(identify, new ErrorInfo(error), null);
                    }

                } else if (SERVICE_GET.equals(identify)) {
                    //  ???????????????????????????????????????????????????????????????????????????????????????????????????

                } else {
                    // ?????????????????????????????????????????????????????????????????????
                    ALog.d(TAG, "?????????????????????????????????????????????????????????set??????");
                    OutputParams outputParams = new OutputParams();
//                    outputParams.put("op", new ValueWrapper.IntValueWrapper(20));
                    itResResponseCallback.onComplete(identify, null, outputParams);
                }
            } catch (Exception e) {
                e.printStackTrace();
                ALog.d(TAG, "TMP ????????????????????????");
            }
        }

        public void onSuccess(Object o, OutputParams outputParams) {
            ALog.d(TAG, "onSuccess() called with: o = [" + o + "], outputParams = [" + outputParams + "]");
            ALog.d(TAG, "??????????????????");
        }

        public void onFail(Object o, ErrorInfo errorInfo) {
            ALog.d(TAG, "onFail() called with: o = [" + o + "], errorInfo = [" + errorInfo + "]");
            ALog.d(TAG, "??????????????????");
        }
    };

    private boolean isValidDouble(String value) {
        if (StringUtils.isEmptyString(value)) {
            return false;
        }
        try {
            if (pattern != null && pattern.matcher(value) != null) {
                if (pattern.matcher(value).matches()) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private Double getDouble(String value) {
        if (isValidDouble(value)) {
            return Double.parseDouble(value);
        }
        return null;
    }

    private boolean isValidInt(String value) {
        if (!StringUtils.isEmptyString(value)) {
            return true;
        }
        return false;
    }


    private int getInt(String value) {
        if (isValidInt(value)) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return DEF_VALUE;
    }
}
