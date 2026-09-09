
package com.helicalinsight.adhoc.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.helicalinsight.datasource.GsonUtility;
import com.helicalinsight.datasource.nosql.NoSQLLoader;
import com.helicalinsight.efw.exceptions.EfwServiceException;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Somen
 * Created on 11/15/2017.
 */

@Component("com.helicalinsight.nosql.mongo")
@Scope("prototype")
public class MongoDrillLoader extends NoSQLLoader {
    @Override
    public boolean loadToMiddleWare(JsonObject formDataJson) {
        JsonObject mongo = new JsonObject();
        String username = formDataJson.get("userName").getAsString();
        String password = formDataJson.get("password").getAsString();
        String jdbcUrl = GsonUtility.optString(formDataJson, "jdbcUrl");
        String host = getHostPort(jdbcUrl, true);
        String port = getHostPort(jdbcUrl, false);
        String storageName = formDataJson.get("name").getAsString();
        String theId = formDataJson.get("theId").getAsString();
        mongo.addProperty("type", "mongo");

        String connectionString = null;
        if (username.isEmpty() || password.isEmpty()) {

            connectionString = "mongodb://" + host + ":" + port;
        } else {
            connectionString = "mongodb://" + username + ":" + password + "@" + host + ":" + port + "/?authMechanism=SCRAM-SHA-1";
        }
        mongo.addProperty("connection", connectionString);
        mongo.addProperty("enabled", true);

        String drillStorageUrl = DrillCsvDataSourceCreator.getUrlOfDrill();

        String resourceUrl = drillStorageUrl + "/storage/" + storageName + "_" + theId + ".json";

        JsonObject storageJson = new JsonObject();
        storageJson.addProperty("name", storageName + "_" + theId);
        storageJson.add("config", mongo);

        String result = DrillCsvDataSourceCreator.drillRestApiCall(resourceUrl, "POST", storageJson.toString());
        if (result == null) {
            throw new EfwServiceException("There was some problem creating drill mongo connection");
        } else {
            try {
                new Gson().fromJson(result, JsonObject.class);

            } catch (JsonSyntaxException e) {
                throw new EfwServiceException("There was a problem " + result);
            }
        }
        return true;
    }

    private String getHostPort(String uri, boolean isHost) {
        String splitArray[] = uri.split(":");
        if (splitArray.length >= 3) {
            if (isHost)
                return splitArray[1].replace("//", "");
            else
                return splitArray[2].substring(0, splitArray[2].indexOf("/"));
        }
        return "";
    }

    @Override
    public boolean testConnection(JsonObject formData) {
        String uri = GsonUtility.optString(formData,"jdbcUrl");
        String database = GsonUtility.optString(formData,"database");
        String username = GsonUtility.optString(formData,"userName");
        String password = GsonUtility.optString(formData,"password");
        if (StringUtils.isEmpty(database)) {
            database = GsonUtility.optString(formData,"databaseName");
        }
        if (StringUtils.isBlank(uri)) {
            return false;
        }

        MongoClient mongo = null;
        try {
            MongoClientURI mongoUri = new MongoClientURI(uri);
            if (StringUtils.isBlank(database)) {
                database = mongoUri.getDatabase();
            }
            if (StringUtils.isBlank(database)) {
                return false;
            }
            if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)
                    && mongoUri.getCredentials() == null) {
                String credentials = username + ":" + password + "@";
                String authenticatedUri = uri.replaceFirst("mongodb(\\+srv)?://", "mongodb$1://" + credentials);
                mongo = new MongoClient(new MongoClientURI(authenticatedUri));
            } else {
                mongo = new MongoClient(mongoUri);
            }
            mongo.getDatabase(database).runCommand(new BasicDBObject("ping", 1));
            return true;
        } catch (MongoException | IllegalArgumentException exception) {
            return false;
        } finally {
            if (mongo != null) {
                mongo.close();
            }
        }
    }
}
