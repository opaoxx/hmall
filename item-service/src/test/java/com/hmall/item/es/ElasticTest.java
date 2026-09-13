package com.hmall.item.es;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.po.ItemDoc;
import com.hmall.item.service.IItemService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.profiles.active=local",
        // Seata 1.5.2 的 CGLIB 初始化不兼容 JDK 17，ES 集成测试不依赖分布式事务。
        "seata.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ElasticTest{

    private RestHighLevelClient client;

    @Autowired
    private IItemService itemService;

    private static final String MAPPING_TEMPLATE =  "{\n" +
            "  \"mappings\": {\n" +
            "    \"properties\": {\n" +
            "      \"id\": {\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"name\":{\n" +
            "        \"type\": \"text\",\n" +
            "        \"analyzer\": \"ik_max_word\"\n" +
            "      },\n" +
            "      \"price\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"stock\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"image\":{\n" +
            "        \"type\": \"keyword\",\n" +
            "        \"index\": false\n" +
            "      },\n" +
            "      \"category\":{\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"brand\":{\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"sold\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"commentCount\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"isAD\":{\n" +
            "        \"type\": \"boolean\"\n" +
            "      },\n" +
            "      \"updateTime\":{\n" +
            "        \"type\": \"date\"\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

    //初始化ES客户端
    @BeforeEach
    void setUp(){
        client = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null){
            client.close();
        }
    }

    @Test
    @Order(1)
    void testConnection(){
        assertTrue(client != null, "ES client should be initialized");
    }

    @Test
    @Order(2)
    void testCreateIndex() throws Exception {
        //准备Request对象
        CreateIndexRequest request = new CreateIndexRequest("items");
        //准备请求参数
        request.source(MAPPING_TEMPLATE, XContentType.JSON);
        //发送请求
        if (client.indices().exists(new GetIndexRequest("items"), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest("items"), RequestOptions.DEFAULT);
        }
        assertTrue(client.indices().create(request, RequestOptions.DEFAULT).isAcknowledged());
    }

    @Test
    @Order(3)
    void testGetIndex() throws Exception {
        //准备Request对象
        GetIndexRequest request = new GetIndexRequest("items");
        //发送请求
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        assertTrue(exists);
    }

    @Test
    @Order(13)
    void testDeleteIndex() throws Exception {
        //准备Request对象
        DeleteIndexRequest request = new DeleteIndexRequest("items");
        //发送请求
        assertTrue(client.indices().delete(request, RequestOptions.DEFAULT).isAcknowledged());
    }

    @Test
    @Order(4)
    void testIndexDoc() throws Exception {
        //准备文档数据
        //根据id查询数据库
        Item item = itemService.getById(100002644680L);
        //将数据转换成文档类型
        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);

        //准备Request对象
        IndexRequest request = new IndexRequest("items").id(itemDoc.getId());

        //准备请求参数
        request.source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);

        //发送请求
        assertEquals("100002644680", client.index(request, RequestOptions.DEFAULT).getId());
    }

    @Test
    @Order(5)
    void testGetDocById() throws Exception {
        //准备Request对象
        GetRequest request = new GetRequest("items", "100002644680");
        //发送请求，得到结果
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        assertTrue(response.isExists());
        ItemDoc itemDoc = JSONUtil.toBean(response.getSourceAsString(), ItemDoc.class);
        assertEquals("100002644680", itemDoc.getId());
    }

    @Test
    @Order(12)
    void testDeleteDoc() throws Exception {
        //准备Request对象
        DeleteRequest request = new DeleteRequest("items", "100002644680");
        //发送请求
        assertEquals("100002644680", client.delete(request, RequestOptions.DEFAULT).getId());
    }

    @Test
    @Order(6)
    void testUpdateDoc() throws Exception {
        //准备Request
        UpdateRequest request = new UpdateRequest("items", "100002644680");
        //准备请求参数
        request.doc("price", 1000);
        //发送请求
        assertEquals("updated", client.update(request, RequestOptions.DEFAULT).getResult().getLowercase());
    }

    @Test
    @Order(7)
    void testMatchAll() throws Exception {
        //准备Request
        SearchRequest request = new SearchRequest("items");
        //准备DSL参数
        request.source().query(QueryBuilders.matchAllQuery());
        //发送请求，得到结果
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            //            System.out.println("response = " + response);

            //解析响应结果
            SearchHits searchHits = response.getHits();
            //查询的总条数
            long total = searchHits.getTotalHits().value;
            System.out.println("total = " + total);

            //查询的结果数组
            SearchHit[] hits = searchHits.getHits();
            for (SearchHit hit : hits) {
                //取出source
                String json = hit.getSourceAsString();
                ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
                System.out.println("itemDoc = " + itemDoc);
            }
    }

    @Test
    @Order(8)
    void testSearch() throws Exception {
        //准备Request
        SearchRequest request = new SearchRequest("items");
        //准备DSL参数
        request.source().query(QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("name", "脱脂牛奶"))
                .filter(QueryBuilders.termQuery("brand", "德亚"))
                .filter(QueryBuilders.rangeQuery("price").gte(10000).lte(30000))
        );
        //发送请求，得到结果
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        assertTrue(response.getHits().getTotalHits().value >= 0);
    }

    @Test
    @Order(9)
    void testSortAndPage() throws Exception {
        int pageNo = 1, pageSize = 10;
        //准备Request
        SearchRequest request = new SearchRequest("items");
        //组织DSL参数
        request.source().query(QueryBuilders.matchAllQuery())
                .sort("sold", SortOrder.DESC)
                .sort("price", SortOrder.ASC)
                .from((pageNo - 1) * pageSize)
                .size(pageSize);

        //发送请求，得到结果
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        assertTrue(response.getHits().getHits().length <= pageSize);
    }

    @Test
    @Order(10)
    void testHighlight() throws Exception {
        //准备Request
        SearchRequest request = new SearchRequest("items");
        //准备DSL参数
        request.source().query(QueryBuilders.matchQuery("name", "脱脂牛奶"))
                .highlighter(new HighlightBuilder().field("name").preTags("<em>").postTags("</em>"));
        //发送请求，得到结果
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            //解析响应结果
            SearchHits searchHits = response.getHits();
            SearchHit[] hits = searchHits.getHits();

            for (SearchHit hit : hits) {
                //取出source
                String json = hit.getSourceAsString();
                ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
                //处理高亮结果
                Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                if(highlightFields != null && !highlightFields.isEmpty()) {
                    String name = highlightFields.get("name").fragments()[0].toString();
                    itemDoc.setName(name);
                }
            }
    }

    @Test
    @Order(11)
    void testAgg() throws Exception {
        //准备Request
        SearchRequest request = new SearchRequest("items");
        //准备DSL参数
        request.source().size(0);
        //构造聚合条件
        String aggName = "brand_agg";
        request.source().aggregation(AggregationBuilders
                .terms(aggName)
                .field("brand")
                .size(10));
        //发送请求，得到结果
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            //解析响应结果
            //根据聚合名称查询聚合
            Aggregations aggregations = response.getAggregations();
            Terms brandAgg = aggregations.get(aggName);
            //获取桶聚合
            List<? extends Terms.Bucket> buckets = brandAgg.getBuckets();
            //遍历
            for (Terms.Bucket bucket : buckets) {
                String brand = bucket.getKeyAsString();
                long docCount = bucket.getDocCount();
                System.out.println("brand = " + brand + ", docCount = " + docCount);
            }
        assertTrue(aggregations != null);
    }
}
