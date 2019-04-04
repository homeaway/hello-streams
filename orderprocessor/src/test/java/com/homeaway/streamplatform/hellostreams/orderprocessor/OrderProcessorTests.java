package com.homeaway.streamplatform.hellostreams.orderprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTest;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import com.homeaway.streamplatform.hellostreams.orderprocessor.dao.CustomerDao;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@GraphQLTest
@RunWith(SpringRunner.class)
@Slf4j
public class OrderProcessorTests {

    private static final String NEW_CUSTOMER_ID = "35442a6a-ad7a-4e72-8af9-5611254306a6";
    @Resource
    private GraphQLTestTemplate graphQLTemplate;

    private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void placeOrderValidCustomer() throws Exception {
        // setup variables
        ObjectNode vars = getPlaceOrderVars(CustomerDao.CUSTOMER_TEST_ID, "Latte");

        // place Order
        GraphQLResponse response = perform("placeOrder.mutation", vars);
        assertThat(response.getStatusCode().value(), is(200));
        assertThat(didGraphQLFail(response), is (false));
        assertThat(response.get("data.placeOrder.customerId", String.class), is(CustomerDao.CUSTOMER_TEST_ID));
    }

    @Test
    public void placeOrderNewCustomer() throws Exception {
        // setup variables
        ObjectNode vars = getPlaceOrderVars(NEW_CUSTOMER_ID, "Latte");

        // place Order
        CustomerDao customerDao = new CustomerDao();
        assertThat(customerDao.getCustomer(NEW_CUSTOMER_ID), is(nullValue()));

        GraphQLResponse response = perform("placeOrder.mutation", vars);
        assertThat(response.getStatusCode().value(), is(200));
        assertThat(didGraphQLFail(response), is (false));
        assertThat(response.get("data.placeOrder.customerId", String.class), is(NEW_CUSTOMER_ID));
    }

    public GraphQLResponse perform(String gqlResource, ObjectNode vars) throws IOException {
        return graphQLTemplate.perform(gqlResource, vars);
    }

    public ObjectNode getPlaceOrderVars(String customerId, String item) {
        ObjectNode vars = mapper.createObjectNode();
        vars.put("customerId", customerId);
        vars.put( "item", item);
        return vars;
    }

    public boolean didGraphQLFail(GraphQLResponse response) throws IOException {
        return response.readTree().get("errors") != null;
    }
}
