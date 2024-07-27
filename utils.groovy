def prepareConfig(){
    def yamlConfig = readYaml text: $YAML_CONFIG

    yamlConfig.each(k, v -> System.setProperty(v))
}

def triggerJob(def jobName) {
    build jof: "$jobName"
}

this