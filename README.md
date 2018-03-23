# Introduction

This project provides transformations for Kafka Connect that will convert XML text to a Kafka Connect
struct based on the configured XML schema. This transformation works by dynamically generating 
JAXB source with [XJC](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/xjc.html) with the
[xjc-kafka-connect-plugin](https://github.com/jcustenborder/xjc-kafka-connect-plugin) loaded. This 
allows the transformation to efficiently convert XML to structured data for Kafka connect.

# Transformations

## FromXML(Key)

This transformation is used to rename fields in the key of an input struct based on a regular expression and a replacement string.

### Configuration

| Name         | Type   | Importance | Default Value | Validator | Documentation                                                   |
| ------------ | ------ | ---------- | ------------- | --------- | --------------------------------------------------------------- |
| schema.path  | List   | High       |               |           | Urls to the schemas to load. http and https paths are supported |


#### Standalone Example

```properties
transforms=Key
transforms.Key.type=com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value
# The following values must be configured.
transforms.Key.schema.path = < Configure me >
```

#### Distributed Example

```json
{
"name": "connector1",
    "config": {
        "connector.class": "com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value",
        "transforms": "Key",
        "transforms.Key.type": "com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value",
        "transforms.Key.schema.path": "< Configure me >"
    }
}
```

## FromXML(Value)

This transformation is used to rename fields in the key of an input struct based on a regular expression and a replacement string.

### Configuration

| Name         | Type   | Importance | Default Value | Validator | Documentation                                                   |
| ------------ | ------ | ---------- | ------------- | --------- | --------------------------------------------------------------- |
| schema.path  | List   | High       |               |           | Urls to the schemas to load. http and https paths are supported |


#### Standalone Example

```properties
transforms=Key
transforms.Key.type=com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value
# The following values must be configured.
transforms.Key.schema.path = < Configure me >
```

#### Distributed Example

```json
{
"name": "connector1",
    "config": {
        "connector.class": "com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value",
        "transforms": "Key",
        "transforms.Key.type": "com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value",
        "transforms.Key.schema.path": "< Configure me >"
    }
}
```