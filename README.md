# Klip + Kotlin Image Processing

- S3 Backed
- Basic Image transforms

# Klip + Kotlin Image Processing

- S3 Backed
- Basic Cropping & caching


Return original image.

```shell
GET http://localhost:8080/img/properties/1/04c08449e1261fedc2eb1a6a9924553.png
```

<img src="https://github.com/kennycason/klip/blob/main/images/original.png?raw=true" width="500px"/>


Resize to 250x250 

```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png
```

<img src="https://github.com/kennycason/klip/blob/main/images/resized.png?raw=true"/>


Resize to 250x250 with Grayscale

```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?grayscale=true
```

<img src="https://github.com/kennycason/klip/blob/main/images/resized_and_grayscale.png?raw=true"/>


Resize to 250x250 with Center Crop

```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?crop=true
```

<img src="https://github.com/kennycason/klip/blob/main/images/cropped.png?raw=true"/>


Resize and Rotate by 45 degrees

```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?rotate=45
```

<img src="https://github.com/kennycason/klip/blob/main/images/resized_rotated45.png?raw=true"/>

Combined Filters

```shell
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?grayscale=true&crop=true&rotate=90
```

<img src="https://github.com/kennycason/klip/blob/main/images/combined_transforms.png?raw=true"/>


## TODO
- Handle crop + resize
- Add caching 
