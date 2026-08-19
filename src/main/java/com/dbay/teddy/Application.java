package com.dbay.teddy;

import com.dbay.teddy.config.AppConfig;
import com.dbay.teddy.utils.TeddyConf;
import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Spring Boot 应用入口
 * @author AlexanderGuo
 */
@SpringBootApplication
@Import(AppConfig.class)
@MapperScan("com.dbay.teddy.mapper")
public class Application{
    private static final String PATH = "p";
    private static final String CONFIG_FILE_ENV = "TEDDY_CONFIG_FILE";
    private static final String DEFAULT_CONFIG_FILE = "conf/teddy.properties";

    public static void main(String[] args) throws ParseException, IOException {

        Options options = new Options();

        options.addOption(PATH, true, "加载 Properties 文件");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        String path = resolveConfigPath(cmd);
        TeddyConf.setProperties(loadProperties(path));

        SpringApplication.run(Application.class, args);
    }

    private static String resolveConfigPath(CommandLine cmd) {
        if (cmd.hasOption(PATH)) {
            return cmd.getOptionValue(PATH);
        }

        String environmentPath = System.getenv(CONFIG_FILE_ENV);
        if (environmentPath != null && !environmentPath.trim().isEmpty()) {
            return environmentPath;
        }

        return DEFAULT_CONFIG_FILE;
    }

    private static Properties loadProperties(String path) throws IOException {
        InputStream in = new FileInputStream(path);
        Properties properties = new Properties();
        properties.load(in);
        IOUtils.closeQuietly(in);
        return properties;
    }

}
