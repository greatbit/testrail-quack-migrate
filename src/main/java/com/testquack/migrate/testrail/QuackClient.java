package com.testquack.migrate.testrail;

import com.testquack.beans.Attribute;
import com.testquack.beans.TestCase;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface QuackClient {
    @POST("{projectId}/testcase/import")
    Call<Void> importTestCases(@Path("projectId") String projectId, @Body List<TestCase> testcases);

    @POST("{projectId}/attribute")
    Call<Attribute> createAttribute(@Path("projectId") String projectId, @Body Attribute attribute);

    @PUT("{projectId}/attribute")
    Call<Attribute> updateAttribute(@Path("projectId") String projectId, @Body Attribute attribute);

    @GET("{projectId}/attribute")
    Call<List<Attribute>> getAllAttributes(@Path("projectId") String projectId);
}
