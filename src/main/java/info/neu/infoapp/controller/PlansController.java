package info.neu.infoapp.controller;

import info.neu.infoapp.configuration.RabbitMQConfig;
import info.neu.infoapp.exception.NotExistingPlanExceptionHandler;
import info.neu.infoapp.model.Plan;
import info.neu.infoapp.model.ResponseObject;
import info.neu.infoapp.service.AuthorizationService;
import info.neu.infoapp.service.PlanService;
import info.neu.infoapp.service.SchemaJsonValidatorService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PlansController {
    private final static String GETALLPLANS = "/getAllPlans";
    private final PlanService planService;
    private final SchemaJsonValidatorService schemaJsonValidatorService;
    private final AuthorizationService authorizationService;


    private final RabbitTemplate template;

    @GetMapping(value = GETALLPLANS)
    ResponseEntity getAllPlans(@RequestHeader HttpHeaders requestHeaders) {
        try {
            String token = requestHeaders.getFirst("Authorization");
            String result = authorizationService.authorize(token);
            if (!result.equals("VALID TOKEN")) {

                ResponseObject r = new ResponseObject("Invalid Token",  HttpStatus.UNAUTHORIZED.value(), new JSONObject().toString());
                return new ResponseEntity<>(r, HttpStatus.UNAUTHORIZED);
            }

            List plans = planService.getAllPlans();
            if (plans == null || plans.isEmpty()) {
                return new ResponseEntity(new ResponseObject("No Plans",  HttpStatus.NOT_FOUND.value(), new ArrayList<Plan>()), HttpStatus.NOT_FOUND);
            }
            ResponseObject r =
                    new ResponseObject("Success",  200, plans);
            return new ResponseEntity<>(plans, HttpStatus.OK);
        } catch (NotExistingPlanExceptionHandler e) {
            ResponseObject r = new ResponseObject("No Plans Found",  404, new ArrayList<>());
            return new ResponseEntity<>(r, HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(value = "/{type}/{planId}",produces ={})
    ResponseEntity getPlan(@PathVariable String type, @PathVariable String planId, @RequestHeader HttpHeaders requestHeaders) throws NotExistingPlanExceptionHandler {
        try {
            String token = requestHeaders.getFirst("Authorization");
            String result = authorizationService.authorize(token);
            if (!result.equals("VALID TOKEN")) {

                ResponseObject r = new ResponseObject("Invalid Token",  HttpStatus.UNAUTHORIZED.value(), new JSONObject().toString());
                return new ResponseEntity<>(r, HttpStatus.UNAUTHORIZED);
            }
            String key = type + ":" + planId;
            Map<String, Object> plan = planService.getPlanById(key);
            if (plan == null || plan.isEmpty()) {
                return new ResponseEntity(new ResponseObject("Provide different plan Id!",  HttpStatus.NOT_FOUND.value(), new ArrayList<Plan>()), HttpStatus.NOT_FOUND);
            } else {
                String ifNoneMatchHeader;
                String ifMatchHeader;
                try {
                    ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");
                    ifMatchHeader = requestHeaders.getFirst("If-Match");
                } catch (Exception e) {
                    ResponseObject r = new ResponseObject("Invalid E-Tag", 200, new ArrayList<Plan>());
                    return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
                }
                String etagFromCache = planService.accessEtag(key);
                if (ifMatchHeader != null && !(ifMatchHeader.equals(etagFromCache))) {
                    ResponseObject r = new ResponseObject("Pre Condition Failed", HttpStatus.PRECONDITION_FAILED.value(), new ArrayList<Plan>());
                    return new ResponseEntity<>( HttpStatus.PRECONDITION_FAILED);
                }

                if ((ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache))) {
                    ResponseObject r = new ResponseObject("", HttpStatus.NOT_MODIFIED.value(), new ArrayList<Plan>());
                    return new ResponseEntity<>( HttpStatus.NOT_MODIFIED);
                }


                if (type.equalsIgnoreCase("plan")) {
                    ResponseObject r = new ResponseObject("Success",  HttpStatus.OK.value(), plan);
                    HttpHeaders httpHeaders = new HttpHeaders();
                    httpHeaders.add("ETag", etagFromCache);
                    httpHeaders.add("Accept","application/json");
                    httpHeaders.add("Content-Type","application/json");
                    return new ResponseEntity<>(new JSONObject(plan).toString(),httpHeaders, HttpStatus.OK);
                } else {
                    HttpHeaders httpHeaders = new HttpHeaders();
                    httpHeaders.add("ETag", etagFromCache);
                    httpHeaders.add("Accept","application/json");
                    httpHeaders.add("Content-Type","application/json");
                    return new ResponseEntity<>(new JSONObject(plan).toString(),httpHeaders, HttpStatus.OK);
                }
            }
        } catch (Exception e) {
            ResponseObject r = new ResponseObject(e.getMessage(), 200, new ArrayList<Plan>());
            return new ResponseEntity<>(r, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping(value = "/newPlan")
    ResponseEntity addPlan(@RequestBody String request, @RequestHeader HttpHeaders requestHeaders) {

        try {
            String token = requestHeaders.getFirst("Authorization");
            String result = authorizationService.authorize(token);
            if (!result.equals("VALID TOKEN")) {

                ResponseObject r = new ResponseObject("Invalid Token",  HttpStatus.UNAUTHORIZED.value(), new JSONObject().toString());
                return new ResponseEntity<>(r, HttpStatus.UNAUTHORIZED);
            }
            JSONObject jsonObjectPlan = new JSONObject(request);
            if (!schemaJsonValidatorService.validateJSONSchema(jsonObjectPlan)) {
                ResponseObject r = new ResponseObject("Provide correct Input",  400, new ArrayList<>());
                return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
            }
            String objectKey = jsonObjectPlan.get("objectType") + ":" + jsonObjectPlan.get("objectId");
            boolean ifExistingPlan = planService.ifKeyExists(objectKey);
            if (!ifExistingPlan) {
                String eTagGeneratedAfterSave = planService.savePlan(jsonObjectPlan, objectKey);
                JSONObject obj = new JSONObject();
                obj.put("ObjectId", jsonObjectPlan.get("objectId"));
                ResponseObject r = new ResponseObject("Plan Successfully Added",  HttpStatus.CREATED.value(), jsonObjectPlan.get("objectId"));
                                Map<String, String> actionMap = new HashMap<>();
                actionMap.put("operation", "SAVE");
                actionMap.put("body", request);
                System.out.println("Sending message: " + actionMap);

                template.convertAndSend(RabbitMQConfig.queueName, actionMap);
                return ResponseEntity.created(new URI("/plan/" + objectKey)).eTag(eTagGeneratedAfterSave)
                        .body(r);
            } else {
                ResponseObject r = new ResponseObject("Already existing plan",  HttpStatus.CONFLICT.value(), new ArrayList<>());
                return new ResponseEntity<>(r, HttpStatus.CONFLICT);
            }
        } catch (Exception e) {
            ResponseObject r = new ResponseObject("BAD_REQUEST",  HttpStatus.BAD_REQUEST.value(), new ArrayList<>());
            return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping(value = "/{type}/{id}")
    ResponseEntity deletePlan(@PathVariable String id, @PathVariable String type, @RequestHeader HttpHeaders requestHeaders) {
        try {
            String token = requestHeaders.getFirst("Authorization");
            String result = authorizationService.authorize(token);
            if (!result.equals("VALID TOKEN")) {

                ResponseObject r = new ResponseObject("Invalid Token",  HttpStatus.UNAUTHORIZED.value(), new JSONObject().toString());
                return new ResponseEntity<>(r, HttpStatus.UNAUTHORIZED);
            }
            String key = type + ":" + id;
            boolean isExistingPlan = planService.ifKeyExists(key);
            if (!isExistingPlan) {
                ResponseObject r = new ResponseObject("id Not found",  HttpStatus.NOT_FOUND.value(), new ArrayList<>());
                return new ResponseEntity<>(r, HttpStatus.NOT_FOUND);
            } else {
                String ifNoneMatchHeader;
                String ifMatchHeader;
                try {
                    ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");
                    ifMatchHeader = requestHeaders.getFirst("If-Match");
                } catch (Exception e) {
                    ResponseObject r = new ResponseObject("Invalid E-Tag Value",  HttpStatus.BAD_REQUEST.value(), new ArrayList<Plan>());
                    return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
                }
                String etagFromCache = planService.accessEtag(key);
                if (ifMatchHeader != null && !(ifMatchHeader.equals(etagFromCache))) {
                    ResponseObject r = new ResponseObject("Pre Condition Failed",  HttpStatus.PRECONDITION_FAILED.value(), new ArrayList<Plan>());
                    return new ResponseEntity<>(r, HttpStatus.PRECONDITION_FAILED);
                }

                if ((ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache))) {
                    ResponseObject r = new ResponseObject("",  HttpStatus.NOT_MODIFIED.value(), new ArrayList<Plan>());
                    return new ResponseEntity<>(r, HttpStatus.NOT_MODIFIED);
                }
                Map<String, Object> plan = planService.getPlanById(key);
                Map<String, String> actionMap = new HashMap<>();
                                actionMap.put("operation", "DELETE");
                actionMap.put("body", new JSONObject(plan).toString());

                System.out.println("Sending message: " + actionMap);

                template.convertAndSend(RabbitMQConfig.queueName, actionMap);
                planService.delete(key);
                ResponseObject r = new ResponseObject("Plan deleted Successfully", HttpStatus.OK.value(), new ArrayList<>());
                return new ResponseEntity<>(r, HttpStatus.OK);
            }
        } catch (NotExistingPlanExceptionHandler e) {
            ResponseObject r = new ResponseObject("Plan Id not found", HttpStatus.NOT_FOUND.value(), new ArrayList<>());
            return new ResponseEntity<>(r, HttpStatus.NOT_FOUND);
        }
    }

    @RequestMapping(method = RequestMethod.PATCH, value = "/{objectType}/{id}")
    ResponseEntity updatePlan(@PathVariable String id, @PathVariable String objectType, @RequestBody String jsonData, @RequestHeader HttpHeaders requestHeaders) {
        try {

            String token = requestHeaders.getFirst("Authorization");
            String result = authorizationService.authorize(token);
            if (!result.equals("VALID TOKEN")) {

                ResponseObject r = new ResponseObject("Invalid Token",  HttpStatus.UNAUTHORIZED.value(), new JSONObject().toString());
                return new ResponseEntity<>(r, HttpStatus.UNAUTHORIZED);
            }

            if (jsonData == null || jsonData.isEmpty()) {

                ResponseObject r = new ResponseObject("Request body is Empty",  HttpStatus.OK.value(), "");
                return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
            }
            JSONObject jsonPlan = new JSONObject(jsonData);

            if (!schemaJsonValidatorService.validateJSONSchema(jsonPlan)) {
                ResponseObject r = new ResponseObject("Provide correct Input", HttpStatus.BAD_REQUEST.value(), new ArrayList<>());
                return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
            }

            String key = objectType + ":" + id;

            if (!planService.ifKeyExists(key)) {
                ResponseObject r = new ResponseObject("Plan not found", 200, new ArrayList<Plan>());
                return new ResponseEntity<>(r, HttpStatus.NOT_FOUND);
            }

            String etagFromCache = planService.accessEtag(key);
            String ifMatchHeader = requestHeaders.getFirst("If-Match");
            String ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");

            if (ifMatchHeader != null && !(ifMatchHeader.equals(etagFromCache))) {
                ResponseObject r = new ResponseObject("Pre Condition Failed", HttpStatus.PRECONDITION_FAILED.value(), new ArrayList<Plan>());
                return new ResponseEntity<>(r, HttpStatus.PRECONDITION_FAILED);
            }

            if ((ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache))) {
                ResponseObject r = new ResponseObject("",  HttpStatus.NOT_MODIFIED.value(), new ArrayList<Plan>());
                return new ResponseEntity<>(r, HttpStatus.NOT_MODIFIED);
            }

            String newEtag = planService.savePlan(jsonPlan, key);
            Map<String, Object> plan = this.planService.getPlanById(key);
            Map<String, String> actionMap = new HashMap<>();
            actionMap.put("operation", "SAVE");
            actionMap.put("body", new JSONObject(plan).toString());

            template.convertAndSend(RabbitMQConfig.queueName, actionMap);
            return ResponseEntity.created(new URI("/" + objectType + "/" + id)).eTag(newEtag).body(plan);

        } catch (RuntimeException e) {
            ResponseObject r = new ResponseObject("Error", HttpStatus.BAD_REQUEST.value(), new ArrayList<Plan>());
            return new ResponseEntity<>( HttpStatus.BAD_REQUEST);
        } catch (URISyntaxException e) {
            ResponseObject r = new ResponseObject("Error", HttpStatus.BAD_REQUEST.value(), new ArrayList<Plan>());
            return new ResponseEntity<>( HttpStatus.BAD_REQUEST);
        } catch (FileNotFoundException e) {
            ResponseObject r = new ResponseObject("Error",  HttpStatus.BAD_REQUEST.value(), new ArrayList<Plan>());
            return new ResponseEntity<>( HttpStatus.BAD_REQUEST);
        }
    }


    @RequestMapping(method = RequestMethod.PUT, value = "/{objectType}/{id}")
    ResponseEntity updateCompletePlan(@PathVariable String id, @PathVariable String objectType, @RequestBody String jsonData, @RequestHeader HttpHeaders requestHeaders) {
        try {

            String token = requestHeaders.getFirst("Authorization");
            String result = authorizationService.authorize(token);
            if (!result.equals("VALID TOKEN")) {

                ResponseObject r = new ResponseObject("Invalid Token", HttpStatus.UNAUTHORIZED.value(), new JSONObject().toString());
                return new ResponseEntity<>(r, HttpStatus.UNAUTHORIZED);
            }

            if (jsonData == null || jsonData.isEmpty()) {

                ResponseObject r = new ResponseObject("Request body is Empty",  HttpStatus.OK.value(), "");
                return new ResponseEntity<>( HttpStatus.BAD_REQUEST);
            }
            JSONObject jsonPlan = new JSONObject(jsonData);
            String key = objectType + ":" + id;

            if (!planService.ifKeyExists(key)) {
                ResponseObject r = new ResponseObject("Plan not found",  200, new ArrayList<Plan>());
                return new ResponseEntity<>( HttpStatus.NOT_FOUND);
            }

            String etagFromCache = planService.accessEtag(key);
            String ifMatchHeader = requestHeaders.getFirst("If-Match");
            String ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");

            if (ifMatchHeader != null && !(ifMatchHeader.equals(etagFromCache))) {
                ResponseObject r = new ResponseObject("Pre Condition Failed", HttpStatus.PRECONDITION_FAILED.value(), new ArrayList<Plan>());
                return new ResponseEntity<>( HttpStatus.PRECONDITION_FAILED);
            }

            if ((ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache))) {
                ResponseObject r = new ResponseObject("",  HttpStatus.NOT_MODIFIED.value(), new ArrayList<Plan>());
                return new ResponseEntity<>( HttpStatus.NOT_MODIFIED);
            }

            String newEtag = planService.savePlan(jsonPlan, key);
            Map<String, Object> plan = this.planService.getPlanById(key);

            ResponseObject r = new ResponseObject("Successfully Put",  200, plan);
            return ResponseEntity.created(new URI("/" + objectType + "/" + id)).eTag(newEtag).body(r);

        } catch (RuntimeException e) {
            ResponseObject r = new ResponseObject("Error", 200, new ArrayList<Plan>());
            return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
        } catch (URISyntaxException e) {
            ResponseObject r = new ResponseObject("Error",200, new ArrayList<Plan>());
            return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
        }
    }
}
