package net.thisptr.jackson.jq.quarkus.deployment;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JacksonJqTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(ParserResource.class));

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Scope scope;

    @Test
    public void testParseEndpoint() throws JsonProcessingException {
        final ParserResource.Document doc = new ParserResource.Document();
        // join this array
        doc.setDocument(objectMapper.readTree("[\"1\", 2, 3, 4, 5, \"6\"]"));
        doc.setExpression("join(\"-\")");

        given().when()
                .contentType(ContentType.JSON)
                .body(objectMapper.writeValueAsString(doc))
                .post("/parser")
                .then()
                .statusCode(200)
                .body(is("[\"1-2-3-4-5-6\"]"));
    }

    @Test
    public void testCompareOutputs() throws JsonProcessingException {
        // stole from https://stackoverflow.com/questions/51837624/jq-split-string-and-assign
        final JsonQuery query = JsonQuery.compile("(.version | split(\".\") | if .[0] == \"0\" then \"beta\"+ .[1] else .[0] end)", Versions.JQ_1_6);
        final JsonNode original = this.objectMapper.readTree("{\n" +
                                                                     "    \"version\" : \"0.1.2\",\n" +
                                                                     "    \"basePath\" : \"/\"\n" +
                                                                     "}");

        List<JsonNode> outQuarkus = new ArrayList<>();
        query.apply(scope, original, outQuarkus::add);
        assertFalse(outQuarkus.isEmpty());

        // default Api
        List<JsonNode> outDefault = new ArrayList<>();
        final Scope defaultScope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, defaultScope);
        query.apply(defaultScope, original, outDefault::add);

        assertEquals(outQuarkus, outDefault);
    }
}
