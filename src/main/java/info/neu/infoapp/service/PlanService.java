package info.neu.infoapp.service;

import lombok.SneakyThrows;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlanService {
    private final static String PLAN = "plan";
    private Jedis jedis;
    private ETagService eTagService;
 //   private IndexingListener indexingListener;

    public PlanService(Jedis jedis, ETagService eTagService) {
        this.jedis = jedis;
        this.eTagService = eTagService;
    //    this.indexingListener = indexingListener;
    }

    public List getAllPlans() {
        List valueList = new ArrayList<>();
        Set<String> keys = jedis.keys("plan:*").stream().
                filter(s -> s.lastIndexOf(":") == s.indexOf(":")).collect(Collectors.toSet());
        for (String key : keys) {
            Map<String, Object> outputMap = new HashMap<>();
            accessOrPurgeData(key, outputMap, false);
            valueList.add(outputMap.values());
        }
        return valueList;
    }

@SneakyThrows
    public String savePlan(JSONObject planObject, String planKey) throws JSONException {
        mapMapper(planObject);


        return this.updateEtag(planKey, planObject);
    }


    public void delete(String id) {
        accessOrPurgeData(id, null, true);
    }

    public Map<String, Map<String, Object>> mapMapper(JSONObject jsonObject) throws JSONException {

        Map<String, Map<String, Object>> objectMap = new HashMap<>();
        Map<String, Object> jsonValueMap = new HashMap<>();
        Iterator<String> jsonObjectKeyIterator = jsonObject.keys();
        while (jsonObjectKeyIterator.hasNext()) {
            String redisKey = jsonObject.get("objectType") + ":" + jsonObject.get("objectId");
            String objectKey = jsonObjectKeyIterator.next();
            Object objectValue = jsonObject.get(objectKey);

            if (objectValue instanceof JSONObject) {
                objectValue = mapMapper((JSONObject) objectValue);
                HashMap<String, Map<String, Object>> objectValueMap = (HashMap<String, Map<String, Object>>) objectValue;
                jedis.sadd(redisKey + ":" + objectKey, objectValueMap.entrySet().iterator().next().getKey());
                System.out.println("Inside Object,redisKey " + redisKey + ":" + objectKey + ":" + " Colon " + objectValueMap.entrySet().iterator().next().getKey());
                jedis.close();

            } else if (objectValue instanceof JSONArray) {
                objectValue = listMapper((JSONArray) objectValue);
                for (HashMap<String, HashMap<String, Object>> entryMap : (List<HashMap<String, HashMap<String, Object>>>) objectValue) {
                    for (String listKeyValue : entryMap.keySet()) {
                        jedis.sadd(redisKey + ":" + objectKey, listKeyValue);
                        jedis.close();
                        System.out.println("Inside Array,redisKey " + redisKey + ":" + objectKey + ":" + " Colon " + listKeyValue);
                    }
                }

            } else {
                jedis.hset(redisKey, objectKey, objectValue.toString());
                System.out.println("RedisKey " + redisKey + " objKey " + objectKey + " val " + objectValue.toString());
                jedis.close();
                jsonValueMap.put(objectKey, objectValue);
                objectMap.put(redisKey, jsonValueMap);
            }
        }
        return objectMap;
    }

    private List<Object> listMapper(JSONArray jsonArray) throws JSONException {
        List<Object> resultList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object objectValue = jsonArray.get(i);
            if (objectValue instanceof JSONArray) {
                objectValue = listMapper((JSONArray) objectValue);
            } else if (objectValue instanceof JSONObject) {
                objectValue = mapMapper((JSONObject) objectValue);
            }
            resultList.add(objectValue);
        }
        return resultList;
    }

    private boolean ifStringInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public Map<String, Object> getPlanById(String keyId) {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        accessOrPurgeData(keyId, resultMap, false);
        return resultMap;
    }

    public boolean ifKeyExists(String objectKey) {
        return jedis.exists(objectKey);

    }

    private Map<String, Object> accessOrPurgeData(String redisKeyValue, Map<String, Object> resultMap, boolean isDeleteFlag) {
        Set<String> keySet = jedis.keys(redisKeyValue + ":*");
        keySet.add(redisKeyValue);
        jedis.close();
        for (String keyVal : keySet) {
            if (keyVal.equals(redisKeyValue)) {
                if (isDeleteFlag) {
                    jedis.del(new String[]{keyVal});
                    jedis.close();
                } else {
                    Map<String, String> keyMap = jedis.hgetAll(keyVal);
                    jedis.close();
                    for (String key : keyMap.keySet()) {
                        if (!key.equalsIgnoreCase("eTag")) {
                            resultMap.put(key,
                                    ifStringInteger(keyMap.get(key)) ? Integer.parseInt(keyMap.get(key)) : keyMap.get(key));
                        }
                    }
                }

            } else {
                String updatedKey = keyVal.substring((redisKeyValue + ":").length());
                Set<String> keySetMembers = jedis.smembers(keyVal);
                jedis.close();
                if (keySetMembers.size() > 1 || updatedKey.equals("linkedPlanServices")) {
                    List<Object> resultList = new ArrayList<Object>();
                    for (String keyMember : keySetMembers) {
                        if (isDeleteFlag) {
                            accessOrPurgeData(keyMember, null, true);
                        } else {
                            Map<String, Object> objectMap = new HashMap<String, Object>();
                            resultList.add(accessOrPurgeData(keyMember, objectMap, false));

                        }
                    }
                    if (isDeleteFlag) {
                        jedis.del(new String[]{keyVal});
                        jedis.close();
                    } else {
                        resultMap.put(updatedKey, resultList);
                    }

                } else {
                    if (isDeleteFlag) {
                        jedis.del(new String[]{keySetMembers.iterator().next(), keyVal});
                        jedis.close();
                    } else {
                        Map<String, String> val = jedis.hgetAll(keySetMembers.iterator().next());
                        jedis.close();
                        Map<String, Object> newMap = new HashMap<String, Object>();
                        for (String name : val.keySet()) {
                            newMap.put(name,
                                    ifStringInteger(val.get(name)) ? Integer.parseInt(val.get(name)) : val.get(name));
                        }
                        resultMap.put(updatedKey, newMap);
                    }
                }
            }
        }
        return resultMap;
    }

    public String accessEtag(String eTagKey) {
        return jedis.hget(eTagKey, "eTag");
    }

    public String updateEtag(String eTagKey, JSONObject eTagValue) {
        String eTag = eTagService.getETag(eTagValue);
        jedis.hset(eTagKey, "eTag", eTag);
        return eTag;
    }


}
