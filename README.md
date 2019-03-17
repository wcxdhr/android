# TensorFlow Detection Demo

修改自[TensorFlow Android Camera Demo](https://github.com/tensorflow/tensorflow/tree/master/tensorflow/examples/android)，保留了并修改了[TF Detect](https://github.com/tensorflow/tensorflow/blob/master/tensorflow/examples/android/src/org/tensorflow/demo/DetectorActivity.java)部分。

## 描述

这个项目基于TensorFlow Android Demo，实现了最简单的人流计数功能。

注意：开发时本人对Android非常不熟悉，因此代码中可能有不规范的地方。

## 增加功能

- 模型远程下载
- 人流统计

## 界面预览

![55282144639](D:\Study\Git\android\images\1552821446394.png)              ![55282158422](D:\Study\Git\android\images\1552821584227.png)

## 用法

1. 下载项目到本地，在Android Studio中编译并运行到Android手机上。
2. 使用[Tensorflow Object Detection API](https://github.com/tensorflow/models/tree/master/research/object_detection/)训练行人模型或直接从[TensorFlow Android Camera Demo](https://github.com/tensorflow/tensorflow/tree/master/tensorflow/examples/android)项目中提取模型文件，改名为ssd-obj.pb，放置到手机的下载目录中即可。
3. 若要更改检测的类目，同时修改assets中的person.pbtxt和模型文件。

## 鸣谢

[TensorFlow](https://github.com/tensorflow/tensorflow)

[guolin](https://blog.csdn.net/guolin_blog)

[BottomBar](https://github.com/roughike/BottomBar)

