# PaintBoardServer

绘板服务端

## APIs

接口 | 方法及参数 | 详细内容
:------------------:|:------------------------------:|:---------------------:
/paintBoard/board | GET                              | 获取绘板内容
/paintBoard/paint | POST(x: Int, y: Int, color: Int) | 进行绘画
/paintBoard/ws    | WebSocket                        | 监听绘板变化的 WebSocket

## How to RUN it

正确配置 JDK 后，在项目根目录运行：

```bash
./gradlew run
```

会在 `localhost:8080` 开启一个服务端。