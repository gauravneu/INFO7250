package info.neu.infoapp.service;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.InputStream;

@Service
public class SchemaJsonValidatorService {
    final static String SCHEMAPATH = "/schema/jsonSchema.json";

    public boolean validateJSONSchema(JSONObject data) throws FileNotFoundException {
        InputStream inputJsonStream = getClass().getResourceAsStream(SCHEMAPATH);
        JSONObject inputJsonObject = new JSONObject(new JSONTokener(inputJsonStream));
        Schema jsonSchema = SchemaLoader.load(inputJsonObject);
        try {
            jsonSchema.validate(data);
            return true;
        } catch (ValidationException vex) {
            vex.printStackTrace();
        }
        return false;
    }
}
