package info.neu.infoapp.controller;

import info.neu.infoapp.model.ResponseObject;
import info.neu.infoapp.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import net.minidev.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;


@RestController
@RequiredArgsConstructor
public class AuthorizationController {

    final static String TOKENURL = "/token";
    final static String TOKEN_VALIDATE_URL = "/validate";


    private final AuthorizationService authorizationService;

    @RequestMapping(method = RequestMethod.GET, value = TOKENURL)
    ResponseEntity getToken() {
        String token;
        try {
            token = authorizationService.generateToken();
            JSONObject obj = new JSONObject();
            obj.put("token", token);
            ResponseObject r = new ResponseObject("Token successfully created!!",  201, obj);
            return new ResponseEntity<>(r, HttpStatus.CREATED);
        } catch (Exception e) {
            ResponseObject r = new ResponseObject("Bad Request",  404, new ArrayList<>());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }


    @RequestMapping(method = RequestMethod.POST, value = TOKEN_VALIDATE_URL)
    ResponseEntity validateToken(@RequestHeader(value = "Authorization") String token) {

        try {
            String isToken = authorizationService.authorize(token);
            ResponseObject r;
            if (isToken.equals("VALID TOKEN")) {
                r = new ResponseObject("Token verified!!", 200, true);
            }
            else if (isToken.equals("TOKEN EXPIRED")) {
                r = new ResponseObject("Token Expired!!", 401, false);
                return new ResponseEntity<>(r, HttpStatus.UNAUTHORIZED);
            }else {
                r = new ResponseObject(isToken,  200, false);
            }
            return new ResponseEntity<>(r, HttpStatus.OK);
        } catch (Exception e) {
            ResponseObject r = new ResponseObject("Bad Request", 404, new ArrayList<>());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
