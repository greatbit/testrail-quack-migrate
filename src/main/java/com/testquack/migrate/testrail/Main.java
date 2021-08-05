package com.testquack.migrate.testrail;

import com.testquack.beans.Attribute;
import com.testquack.beans.AttributeValue;
import com.testquack.beans.TestCase;
import com.testquack.client.HttpClientBuilder;
import generated.Case;
import generated.Section;
import generated.Step;
import generated.Suite;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.util.*;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class Main {
    private static Attribute typeAttr;
    private static Attribute priorityAttr;
    private static Map<Integer, Attribute> depthAttributesMap = new HashMap<>();
    private static QuackClient quackClient;
    private static String projectId;

    public static void main(String[] args) throws IOException, JAXBException, SAXException {
        if (args.length < 4){
            throw new RuntimeException("Wrong arguments. \n " +
                    "Four parameters must be specified - api endpoint, projectId, absolute path to xml file and an api token\n" +
                    "E.g.: java -jar migrate-testrail.jar PROJECT_ID FILE_PATH TOKEN");
        }

        final String aoiEndpoint = args[0];
        projectId = args[1];
        final String filePath = args[2];
        final String token = args[3];

        JAXBContext contextObj = JAXBContext.newInstance(Suite.class);
        Unmarshaller unmarshallerObj = contextObj.createUnmarshaller();
        File initialFile = new File(filePath);
        InputStream targetStream = new FileInputStream(initialFile);
        Suite suite = (Suite) unmarshallerObj.unmarshal(targetStream);

        List<TestCase> testCases = new ArrayList<>();

        quackClient = getClient(token, aoiEndpoint, 60000);
        List<Attribute> existingAttributes = quackClient.getAllAttributes(projectId).execute().body();
        typeAttr = existingAttributes.stream().filter(attribute -> attribute.getName().toLowerCase().equals("type")).findFirst().orElse(null);
        priorityAttr = existingAttributes.stream().filter(attribute -> attribute.getName().toLowerCase().equals("priority")).findFirst().orElse(null);

        if (typeAttr == null){
            typeAttr = quackClient.createAttribute(projectId, new Attribute().withName("Type")).execute().body();
        }
        if (priorityAttr == null){
            priorityAttr = quackClient.createAttribute(projectId, new Attribute().withName("Priority")).execute().body();
        }

        int maxDepth = 0;
        for(Section section : suite.getSections()){
            maxDepth = Math.max(maxDepth, traverse(testCases, new ArrayList<>(), section));
        }

        // Update attributes
        quackClient.updateAttribute(projectId, typeAttr).execute();
        quackClient.updateAttribute(projectId, priorityAttr).execute();

        for(Attribute attribute: depthAttributesMap.values()){
            quackClient.updateAttribute(projectId, attribute).execute();
        }

        testCases.forEach(testCase -> System.out.println("Case name: " + testCase.getName()));


        //Import testcases
        quackClient.importTestCases(projectId, testCases).execute();
    }

    private static int traverse(List<TestCase> testCases, List<String> treePath, Section section) throws IOException {
        treePath.add(section.getName());
        int childDepth = 0;

        for(Case testCase: section.getCases()){
            testCases.add(convertTestcase(testCase, treePath));
        }

        for(Section childSection: section.getSections()){
            childDepth = Math.max(childDepth, traverse(testCases, treePath, childSection));
        }

        return childDepth + 1;
    }

    private static TestCase convertTestcase(Case railCase, List<String> treePath) throws IOException {
        TestCase testCase = (TestCase) new TestCase()
                .withAlias(railCase.getId())
                .withAutomated(railCase.getCustom().getAutomationType() != null && !"0".equals(railCase.getCustom().getAutomationType().getId()))
//                .withDescription()
                .withImportResource("Test Rail")
                .withName(railCase.getTitle())
                .withPreconditions(railCase.getCustom().getPreconds())
                .withSteps(convertSteps(railCase));

        testCase.getAttributes().put(typeAttr.getId(), new HashSet<>(singletonList(railCase.getType())));
        addValueToAttribute(typeAttr, railCase.getType());

        testCase.getAttributes().put(priorityAttr.getId(), new HashSet<>(singletonList(railCase.getPriority())));
        addValueToAttribute(priorityAttr, railCase.getPriority());

        for (int idx = 0; idx < treePath.size(); idx++){
            String attrValue = treePath.get(idx);
            if (!depthAttributesMap.containsKey(idx)){
                Attribute newLevelAttribute =  quackClient.createAttribute(projectId, new Attribute().withName(getAttributeName(idx))).execute().body();
                depthAttributesMap.put(idx, newLevelAttribute);
            }
            Attribute levelAttribute = depthAttributesMap.get(idx);

            addValueToAttribute(levelAttribute, attrValue);
            testCase.getAttributes().put(levelAttribute.getId(), new HashSet<>(singletonList(attrValue)));
        }

        return testCase;
    }

    private static void addValueToAttribute(Attribute attribute, String attrValue){
        if (attribute.getAttrValues().stream().map(AttributeValue::getValue).noneMatch(val -> val.equals(attrValue))){
            attribute.getAttrValues().add(new AttributeValue().withUuid(UUID.randomUUID().toString()).withValue(attrValue));
        }
    }

    private static String getAttributeName(int index){
        return "Attribute " + index;
    }

    private static List<com.testquack.beans.Step> convertSteps(Case railCase) {
        return railCase.getCustom().getStepsSeparated().stream().map(step -> convertStep(step)).collect(toList());
    }

    private static com.testquack.beans.Step convertStep(Step step) {
        return new com.testquack.beans.Step().withAction(step.getContent()).withExpectation(step.getExpected());
    }


    public static QuackClient getClient(String apiToken, String quackApiEndpoint, long quackApiTimeoutMs) {
        return HttpClientBuilder.builder(quackApiEndpoint, quackApiTimeoutMs, apiToken).build().
                create(QuackClient.class);
    }
}
