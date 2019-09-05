import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.Providers;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.google.common.base.CaseFormat;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Objects;

/**
 * @author Yu Guanhua
 * @date 2019/8/8
 */
public class MyParser {
    public static void main(String[] args) throws FileNotFoundException {
        /* 读取domain文件夹下的所有类 */
        // domain文件夹绝对路径
        //var dirPath = "C:\\workspace\\zhidun-system-server\\training-service\\src\\main\\java\\com\\zhidun\\trainingservice\\domain";
        //var dirPath = "C:\\workspace\\zhidun-system-server\\cms-service\\src\\main\\java\\com\\zhidun\\cmsservice\\domain";
        // var dirPath = "C:\\workspace\\zhidun-system-server\\mall-service\\src\\main\\java\\com\\zhidun\\mallservice\\domain";
        var dirPath = "C:\\workspace\\zhidun-system-server\\main-service\\src\\main\\java\\com\\zhidun\\mainservice\\domain";
        //var dirPath = "C:\\workspace\\zhidun-system-server\\storage-service\\src\\main\\java\\com\\zhidun\\storageservice\\domain";
        var directory = new File(dirPath);
        // 临时存放表信息的容器
        var tmpTableContainer = new HashMap<String, String>();
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                continue;
            }
            /*if (!file.getName().endsWith("Column.java")) {
                continue;
            }*/
            /* 从临时存放表信息的容器中取出数据（如果里面有存数据） */
            if (tmpTableContainer.size() > 0) {
                var anotherTableName = tmpTableContainer.get("anotherTableName");
                var anotherTableFieldName1 = tmpTableContainer.get("anotherTableFieldName1");
                var anotherTableFieldName2 = tmpTableContainer.get("anotherTableFieldName2");
                var anotherTableFieldName3 = tmpTableContainer.get("anotherTableFieldName3");
                var anotherTableComment = tmpTableContainer.get("anotherTableComment");
                System.out.println("表名：" + anotherTableName + "[" + anotherTableComment + "，多对多关联表]");
                System.out.println("字段1：" + anotherTableFieldName1);
                System.out.println("字段2：" + anotherTableFieldName2);
                if (anotherTableFieldName3 != null) {
                    System.out.println("字段3：" + anotherTableFieldName3);
                }
                tmpTableContainer.clear();
                System.out.println("--------------------------------------------------------------------------------------");
            }
            /* 排除接口文件 */
            if (file.getName().endsWith("Interface.java") || file.getName().endsWith("BaseDomain.java")) {
                continue;
            }
            /* 类名 -> 表名 */
            var className = file.getName().split(".java")[0];
            // 表名
            // 类名称前面连续的大写字母改成小写，适应spring jpa的明明规则
            var finalClassNamePrefix = new StringBuilder();
            for (int i = 0; i < className.length(); i++) {
                if (Character.isUpperCase(className.charAt(i))) {
                    finalClassNamePrefix.append(Character.toLowerCase(className.charAt(i)));
                } else {
                    className = finalClassNamePrefix.toString() + className.substring(i);
                    break;
                }
            }
            var tableName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, className);
            /* 类注释 */
            var javaParser = new JavaParser();
            ParseResult parseResult = javaParser.parse(ParseStart.COMPILATION_UNIT,
                    Providers.provider(file));
            parseResult.ifSuccessful(cu -> {
                CompilationUnit realCu = (CompilationUnit) cu;
                // 表注释
                var tableComment = realCu.getComments().get(0).getContent().split(System.getProperty("line.separator"))[1].trim().replace("* ", "");
                var classAnnotations = realCu.getTypes().get(0).getAnnotations();
                if (classAnnotations != null) {
                    // 只处理有@Entity注解的实体类
                    if (classAnnotations.stream().anyMatch(a -> Objects.equals("Entity", a.getName().asString()))) {
                        System.out.println("表名：" + tableName + "[" + tableComment + "]");

                        int[] i = {1};
                        ((CompilationUnit) cu).getTypes().forEach(typeDeclaration -> {
                            typeDeclaration.getMembers().forEach(member -> {
                                try {
                                    // 是否跳过本个字段
                                    var skipThisField = false;
                                    var field = (FieldDeclaration) member;

                                    String fieldName = null;
                                    String fieldComment = null;
                                    /* 成员变量的注解们 */
                                    var annotations = field.getAnnotations();
                                    if (annotations != null) {
                                        for (AnnotationExpr annotation : annotations) {
                                            var annotationName = annotation.getName().asString();
                                            // OneToMany注解的字段不是数据库字段
                                            if (annotationName.equals("OneToMany")) {
                                                skipThisField = true;
                                            }
                                            // JoinColumn字段的数据库字段名是该注解的name属性
                                            if (annotationName.equals("JoinColumn")) {
                                                fieldName = annotation.getChildNodes().get(1)
                                                        .getChildNodes().get(1)
                                                        .toString().replace("\"", "");
                                                // 如果JoinColumn的值一大堆，如cms系统里的Column，那么忽略这个字段
                                                if (annotation.getChildNodes().size() > 2) {
                                                    skipThisField = true;
                                                }
                                            }
                                            // 如果是@ID注解，那就是主键id
                                            if (annotationName.equals("Id")) {
                                                fieldComment = "主键";
                                            }
                                            // 如果是注解为临时的变量，跳过
                                            if (annotationName.equals("Transient")) {
                                                skipThisField = true;
                                            }
                                            // 如果是注解为JoinTable，多对多关系。则要有一个新的表解释
                                            if (annotationName.equals("JoinTable")) {
                                                // 表名
                                                var anotherTableName = annotation.getChildNodes().get(1).getChildNodes().get(1)
                                                        .toString().replace("\"", "");
                                                // 另一个表注释（manytomany变量的注释）
                                                var anotherTableComment = getCommentFromField(field);
                                                // 第一个字段名称
                                                var anotherTableFieldName1 = annotation.getChildNodes().get(2).getChildNodes().get(1).getChildNodes().get(1).getChildNodes().get(1)
                                                        .toString()
                                                        .replace("\"", "");
                                                String anotherTableFieldName2 = null;
                                                String anotherTableFieldName3 = null;
                                                /* 如果成员变量的类型是Map（拥有MapKeyColumn注解） */
                                                if (annotations.stream().anyMatch(a -> a.getName().asString().equals("MapKeyColumn"))) {
                                                    for (AnnotationExpr a : annotations) {
                                                        // 第三个字段名称（@MapKeyColumn）
                                                        if (a.getName().asString().equals("MapKeyColumn")) {
                                                            anotherTableFieldName3 = a.getChildNodes().get(1).getChildNodes().get(1).toString().replace("\"", "");
                                                        }
                                                        // 第二个字段名称（@Column的name）
                                                        if (a.getName().asString().equals("Column")) {
                                                            anotherTableFieldName2 = a.getChildNodes().get(1).getChildNodes().get(1).toString().replace("\"", "");
                                                        }
                                                    }
                                                } else {
                                                    // 其余字段名称
                                                    anotherTableFieldName2 = annotation.getChildNodes().get(3).getChildNodes().get(1).getChildNodes().get(1).getChildNodes().get(1)
                                                            .toString()
                                                            .replace("\"", "");
                                                }
                                                // 多对多表的数据放到全局容器中，下次循环时打印
                                                tmpTableContainer.put("anotherTableName", anotherTableName);
                                                tmpTableContainer.put("anotherTableComment", tableComment + anotherTableComment);
                                                tmpTableContainer.put("anotherTableFieldName1", anotherTableFieldName1);
                                                tmpTableContainer.put("anotherTableFieldName2", anotherTableFieldName2);
                                                tmpTableContainer.put("anotherTableFieldName3", anotherTableFieldName3);
                                                skipThisField = true;
                                            }
                                        }
                                    }
                                    var hasApiModelProperty = field.getAnnotations().stream().anyMatch(a -> a.getName().asString().equals("ApiModelProperty"));
                                    if (!skipThisField) {
                                        if (field.getComment().isPresent() || fieldComment != null || hasApiModelProperty) {
                                            // 字段名
                                            if (fieldName == null) {
                                                fieldName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getVariable(0).getName().asString());
                                            }
                                            // 字段类型
                                            var fieldType = field.getCommonType().asString();
                                            // 字段注释
                                            if (fieldComment == null) {
                                                fieldComment = getCommentFromField(field);
                                            }

                                            System.out.println("字段" + i[0] + "：" + fieldName + "(" + fieldComment + ")");
                                            i[0] = i[0] + 1;
                                        }
                                    }
                                } catch (ClassCastException e) {
                                }
                            });
                        });
                    }
                }
            });
            System.out.println("--------------------------------------------------------------------------------------");
        }
    }

    private static String getCommentFromField(FieldDeclaration field) {
        String fieldComment = "";
        // 如果是@ApiModelProperty注解的注释，使用它，否则按原始的文档注释来解析
        if (field.getAnnotations().stream().anyMatch(a -> a.getName().asString().equals("ApiModelProperty"))) {
            for (AnnotationExpr annotation : field.getAnnotations()) {
                if (annotation.getName().asString().equals("ApiModelProperty")) {
                    if (annotation.getChildNodes().size() == 2) {
                        fieldComment = annotation.getChildNodes().get(1).toString().replace("\"", "");
                    } else {
                        fieldComment = annotation.getChildNodes().get(1).getChildNodes().get(1).toString().replace("\"", "");
                    }
                    break;
                }
            }
        } else {
            fieldComment = field.getComment().get().getContent().trim().replace("* ", "");
        }
        return fieldComment;
    }
}
