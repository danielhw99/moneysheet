package com.danielhw99.common;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import com.danielhw99.common.config.OpenApiConfiguration;
import com.danielhw99.auth.security.AuthOpenApiConfiguration;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.junit.jupiter.api.Test;
class SwaggerDomainTest {
    @Test void customizersAlsoApplyToGroupedDocuments() {
        assertThat(new AuthOpenApiConfiguration().sessionAndCsrfDocumentation()).isInstanceOf(GlobalOpenApiCustomizer.class);
        assertThat(new OpenApiConfiguration().closedDtoSchemas()).isInstanceOf(GlobalOpenApiCustomizer.class);
    }
    @Test void groupedDocumentsReceiveTheErrorSchemaEvenWithoutBaseComponents() {
        var api = new io.swagger.v3.oas.models.OpenAPI().components(new io.swagger.v3.oas.models.Components());
        new OpenApiConfiguration().closedDtoSchemas().customise(api);
        assertThat(api.getComponents().getSchemas()).containsKey("ApiError");
    }
    @Test void transactionResponsesDeclareAllRequiredFields() {
        var converter = io.swagger.v3.core.converter.ModelConverters.getInstance();
        var response = converter.resolveAsResolvedSchema(new io.swagger.v3.core.converter.AnnotatedType(com.danielhw99.transaction.api.TransactionResponse.class));
        assertThat(response.schema.getRequired()).containsExactlyInAnyOrder("id","date","time","title","amount","direction","categoryId");
        var page = converter.resolveAsResolvedSchema(new io.swagger.v3.core.converter.AnnotatedType(com.danielhw99.transaction.api.TransactionList.class));
        assertThat(page.schema.getRequired()).containsExactlyInAnyOrder("rows","nextCursor","expenseTotal","incomeTotal","total");
    }
    @Test void sevenIndependentDomainGroupsAreDeclared() throws Exception {
        var methods = Arrays.stream(OpenApiConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getReturnType() == GroupedOpenApi.class).toList();
        assertThat(methods).hasSize(7);
        var names = new java.util.ArrayList<String>();
        for (var method : methods) names.add(((GroupedOpenApi) method.invoke(new OpenApiConfiguration())).getGroup());
        assertThat(names).containsExactlyInAnyOrder("auth", "categories", "budgets", "transactions", "memos", "payments", "cash-flows");
    }
    @Test void memoResponsesDeclareAllRequiredFields() {
        var converter=io.swagger.v3.core.converter.ModelConverters.getInstance();
        var response=converter.resolveAsResolvedSchema(new io.swagger.v3.core.converter.AnnotatedType(com.danielhw99.memo.api.MemoResponse.class));
        assertThat(response.schema.getRequired()).containsExactlyInAnyOrder("id","date","time","title","amount","direction","completedOn");
        var page=converter.resolveAsResolvedSchema(new io.swagger.v3.core.converter.AnnotatedType(com.danielhw99.memo.api.MemoList.class));
        assertThat(page.schema.getRequired()).containsExactlyInAnyOrder("rows","nextCursor","expenseTotal","incomeTotal","total","pendingMemoTotal");
    }
    @Test void paymentResponsesDeclareAllRequiredFields() {
        var converter=io.swagger.v3.core.converter.ModelConverters.getInstance();
        var response=converter.resolveAsResolvedSchema(new io.swagger.v3.core.converter.AnnotatedType(com.danielhw99.payment.api.PaymentResponse.class));
        assertThat(response.schema.getRequired()).containsExactlyInAnyOrder("id","date","time","title","amount","budgetItemId");
        var page=converter.resolveAsResolvedSchema(new io.swagger.v3.core.converter.AnnotatedType(com.danielhw99.payment.api.PaymentList.class));
        assertThat(page.schema.getRequired()).containsExactlyInAnyOrder("rows","nextCursor","expenseTotal","incomeTotal","total");
    }
}
