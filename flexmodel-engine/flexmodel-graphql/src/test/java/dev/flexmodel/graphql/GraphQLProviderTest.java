package dev.flexmodel.graphql;

import graphql.ExecutionResult;
import graphql.GraphQL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static dev.flexmodel.graphql.Models.*;

/**
 * @author cjbi
 */
public class GraphQLProviderTest extends AbstractIntegrationTest {

  @Test
  void testQuery() {
    String classesEntityName = "testQueryClasses";
    String studentEntityName = "testQueryStudent";
    String studentDetailEntityName = "testQueryStudentDetail";
    String courseEntityName = "testQueryCourse";
    String teacherEntityName = "testQueryTeacher";
    createClassesEntity(session, classesEntityName);
    createStudentEntity(session, studentEntityName);
    createStudentDetailEntity(session, studentDetailEntityName);
    createCourseEntity(session, courseEntityName);
    createTeacherEntity(session, teacherEntityName);
    createAssociations(session, classesEntityName, studentEntityName, studentDetailEntityName, courseEntityName, teacherEntityName);
    createCourseData(session, courseEntityName);
    createClassesData(session, classesEntityName);
    createStudentData(session, studentEntityName);
    createTeacherData(session, teacherEntityName);

    FlexmodelGraphQL graphQLProvider = new FlexmodelGraphQL();
    GraphQL graphQL = graphQLProvider.generateGraphQLWithSchemaObject(sessionFactory, "system");
    // 创建查询
    String query = """
      query {
        classes: testQueryClasses(
         page: 1,
         size:1,
         where: {classCode: {_eq: "C_001"}, and: [{className: {_eq: "一年级1班"}}, {or: [{classCode: { _eq: "C_002"}}]}]}
        ) {
          id, classCode, className, students { name: studentName }
        }
        students: testQueryStudent(
          size: 3
          page: 1
          orderBy: {classId: asc, id: desc}
        ) {
          id, studentName
        }
        teachers: testQueryTeacher {
         id, teacherName
        }
        aggregate: testQueryStudentAggregate {
           _count
        }
        aggregate2: testQueryStudentAggregate {
           total: _count(distinct: true, field: id)
           _max {
             id, age
           }
           _min {
             id, age
           }
           _sum {
             age
           }
           _avg {
             age
           }
        }
        testQueryStudent {
          age
          classId
          gender
          id
          studentName
          _join {
            testQueryStudentAggregate {
              _count
            }
            testQueryStudent(size:10, page:1) {
              age
              classId
              gender
              id
              studentName
            }
          }
        }
      }
      """;
    ExecutionResult executionResult = graphQL.execute(query);
    Map<String, Object> data = executionResult.getData();
    // 打印结果
    System.out.println(executionResult);
    Assertions.assertNotNull(data);
  }

  @Test
  void testDirective() {
    String classesEntityName = "testDirectiveClasses";
    String studentEntityName = "testDirectiveStudent";
    String studentDetailEntityName = "testDirectiveStudentDetail";
    String courseEntityName = "testDirectiveCourse";
    String teacherEntityName = "testDirectiveTeacher";
    createClassesEntity(session, classesEntityName);
    createStudentEntity(session, studentEntityName);
    createStudentDetailEntity(session, studentDetailEntityName);
    createCourseEntity(session, courseEntityName);
    createTeacherEntity(session, teacherEntityName);
    createAssociations(session, classesEntityName, studentEntityName, studentDetailEntityName, courseEntityName, teacherEntityName);
    createCourseData(session, courseEntityName);
    createClassesData(session, classesEntityName);
    createStudentData(session, studentEntityName);
    createTeacherData(session, teacherEntityName);

    FlexmodelGraphQL graphQLProvider = new FlexmodelGraphQL();
    GraphQL graphQL = graphQLProvider.generateGraphQLWithSchemaObject(sessionFactory, "system");
    String query = """
      query {
        list: testDirectiveStudent {
          classId
          studentName
        }
        total: testDirectiveStudentAggregate @transform(get: "_count") {
           _count
        }
        avgAge: testDirectiveStudentAggregate @transform(get: "_avg.age") {
          _avg {
            age
          }
        }
      }
      """;
    ExecutionResult executionResult = graphQL.execute(query);
    Map<String, Object> data = executionResult.getData();
    // 打印结果
    System.out.println(executionResult);
    Assertions.assertNotNull(data);
    Assertions.assertNotNull(data.get("list"));
    Assertions.assertEquals(3, data.get("total"));
    Assertions.assertInstanceOf(Number.class, data.get("avgAge"));
  }

  @Test
  void testJoin() {
    String classesEntityName = "testJoinClasses";
    String studentEntityName = "testJoinStudent";
    String studentDetailEntityName = "testJoinStudentDetail";
    String courseEntityName = "testJoinCourse";
    String teacherEntityName = "testJoinTeacher";
    createClassesEntity(session, classesEntityName);
    createStudentEntity(session, studentEntityName);
    createStudentDetailEntity(session, studentDetailEntityName);
    createCourseEntity(session, courseEntityName);
    createTeacherEntity(session, teacherEntityName);
    createAssociations(session, classesEntityName, studentEntityName, studentDetailEntityName, courseEntityName, teacherEntityName);
    createCourseData(session, courseEntityName);
    createClassesData(session, classesEntityName);
    createStudentData(session, studentEntityName);
    createTeacherData(session, teacherEntityName);

    FlexmodelGraphQL graphQLProvider = new FlexmodelGraphQL();
    GraphQL graphQL = graphQLProvider.generateGraphQLWithSchemaObject(sessionFactory, "system");
    String query = """
      query ($classId: Int = 1, $classById: ID = 1, $studentId: Int @internal) {
        class: testJoinClassesById(id: $classById) {
          className
          id
          _join {
            students: testJoinStudent(where: { classId: { _eq: $classId } }) {
              id @export(as: "studentId")
              studentName
              _join {
                detail: testJoinStudentDetail(size: 1, where: { studentId: { _eq: $studentId } }) {
                  description
                }
              }
            }
            total: testJoinStudentAggregate(where: { classId: { _eq: $classId } }) {
              _count
            }
          }
        }
      }
      """;
    ExecutionResult executionResult = graphQL.execute(b -> b.query(query).variables(Map.of("$studentId", 1)));
    Map<String, Object> data = executionResult.getData();
    // 打印结果
    System.out.println(executionResult);
    Assertions.assertNotNull(data);
  }


  @Test
  void testMutation() {
    String classesEntityName = "testMutationClasses";
    String studentEntityName = "testMutationStudent";
    String studentDetailEntityName = "testMutationStudentDetail";
    String courseEntityName = "testMutationCourse";
    String teacherEntityName = "testMutationTeacher";
    createClassesEntity(session, classesEntityName);
    createStudentEntity(session, studentEntityName);
    createStudentDetailEntity(session, studentDetailEntityName);
    createCourseEntity(session, courseEntityName);
    createTeacherEntity(session, teacherEntityName);
    createAssociations(session, classesEntityName, studentEntityName, studentDetailEntityName, courseEntityName, teacherEntityName);
    createCourseData(session, courseEntityName);
    createClassesData(session, classesEntityName);
    createStudentData(session, studentEntityName);
    createTeacherData(session, teacherEntityName);

    FlexmodelGraphQL graphQLProvider = new FlexmodelGraphQL();
    GraphQL graphQL = graphQLProvider.generateGraphQLWithSchemaObject(sessionFactory, "system");
    // 创建查询
    String query = """
      mutation {
        class: createTestMutationClasses(data:{className: "测试班级", classCode: "TestC"}) {
          classCode
        }
        course: createTestMutationCourse(data: {courseName: "测试课程", courseNo: "Test_CC"}) {
            courseName
            courseNo
        }
        student: createTestMutationStudent(data: {studentName: "张三丰", gender: MALE, age: 200, classId: 1, remark: {test:"aa"}}) {
            id
            remark
        }
      }
      """;
    ExecutionResult executionResult = graphQL.execute(query);
    log.info("query result: {}", executionResult);
    Map<String, Object> data = executionResult.getData();
    Assertions.assertNotNull(data.get("class"));
    Assertions.assertNotNull(data.get("course"));
    Assertions.assertNotNull(data.get("student"));
    String query2 = """
      mutation MyMutation($studentId: ID!, $courseNo: ID!) {
        class: deleteTestMutationCourseById(id: $courseNo) {
          courseNo
        }
        student: updateTestMutationStudentById(set: {age: 199, remark: {test: "bb"}}, id: $studentId) {
          id
        }
      }
      """;
    Map<String, Object> variables = new HashMap<>();
    if (data.get("student") instanceof Map<?, ?> map) {
      variables.put("studentId", map.get("id"));
    }
    variables.put("courseNo", "Test_CC");
    ExecutionResult executionResult2 = graphQL.execute(i -> i.query(query2).variables(variables));
    log.info("query result: {}", executionResult2);
    Map<String, Object> data2 = executionResult2.getData();
    Assertions.assertNotNull(data2.get("class"));
  }

}
