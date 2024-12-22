# Klip + Kotlin Image Processing

- S3 Backed
- Basic Cropping & caching


Return original image.

```shell
GET http://localhost:8080/img/properties/1/04c08449e1261fedc2eb1a6a9924553.png
```

Resize to 250x250 

```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png
```

Resize to 250x250 with Grayscale
```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?grayscale=true
```

Resize to 250x250 with Center Crop

```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?crop=true
```

Resize and Rotate by 45 degrees

```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?rotate=45
```

Combined Filters

```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?grayscale=true&crop=true&rotate=90
```
