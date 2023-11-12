package ru.karimov;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;



@Mojo(name = "generate", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class JsonSchemaGenerator extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    public MavenProject project;

    @Parameter(property = "basePackage", required = true)
    public String basePackage;

    private final Log log = getLog();

    public void execute() throws MojoExecutionException {
        log.info("Base package for scanning: " + basePackage);
        log.info("Project parameter: " + project);

        String basePath = "json-schemes/output";
        File baseDir = new File(basePath);
        if(!baseDir.exists()) {
            baseDir.mkdirs();
        }

        Reflections reflections = new Reflections(getClassLoader(),
                basePackage,
                new SubTypesScanner(false));

        Set<Class<? extends Object>> allClasses = reflections.getSubTypesOf(Object.class);
        log.info("Class qty: " + allClasses.size());

        for (Class<?> a : allClasses) {
            String className = a.getName();
            String dirClass = className.replaceAll("\\.", "/");
            System.out.println(dirClass);
            String classPath = basePath + "/" + dirClass;
            File dir = new File(classPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            SchemaGenerator generator = getGenerator();
            JsonNode node = generator.generateSchema(a);
            System.out.println(node);

            String jsonPath = classPath + "/" + a.getSimpleName() + ".json";
            System.out.println(jsonPath);
            File file = new File(jsonPath);
            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(file);
                outputStream.write(node.toPrettyString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
                throw new MojoExecutionException(e.getMessage());
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private SchemaGenerator getGenerator() {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_6,
                OptionPreset.PLAIN_JSON);
        SchemaGeneratorConfig config = configBuilder
                .without(Option.FLATTENED_ENUMS_FROM_TOSTRING)
                .with(Option.NULLABLE_FIELDS_BY_DEFAULT)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                .with(Option.NULLABLE_ARRAY_ITEMS_ALLOWED)
                .build();

        configBuilder.forFields().withStringMaxLengthResolver(fieldScope ->
                fieldScope.getType().getErasedType().equals(String.class) ? 255 : null);

        configBuilder.forTypesInGeneral().withArrayMaxItemsResolver(a -> 255);

        configBuilder.forFields().withNumberInclusiveMaximumResolver(scope ->
                numberPredicate(scope) ? BigDecimal.valueOf(214748624) : null);
        configBuilder.forFields().withNumberInclusiveMinimumResolver(scope ->
                numberPredicate(scope) ? BigDecimal.valueOf(-214748624) : null);

        return new SchemaGenerator(config);
    }

    private boolean numberPredicate(FieldScope scope) {
        Class<?> type = scope.getType().getErasedType();
        return type.equals(int.class) ||
                type.equals(long.class) ||
                type.equals(byte.class) ||
                type.equals(short.class) ||
                type.equals(float.class) ||
                type.equals(double.class) ||
                Number.class.isAssignableFrom(type);
    }

    private ClassLoader getClassLoader() {
        ClassLoader classLoader = null;
        try {
            List<String> classpathElements = project.getRuntimeClasspathElements();
            if (null == classpathElements) {
                return Thread.currentThread().getContextClassLoader();
            }
            URL[] urls = new URL[classpathElements.size()];

            for (int i = 0; i < classpathElements.size(); ++i) {
                urls[i] = new File((String) classpathElements.get(i)).toURI().toURL();
            }
            classLoader = new URLClassLoader(urls, getClass().getClassLoader());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classLoader;
    }

}


