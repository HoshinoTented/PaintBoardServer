# PaintBoardServer

绘板服务端

[Have a try](http://106.15.202.51:8080/paintBoard)

## APIs

接口 | 方法及参数 | 详细内容
:------------------:|:----------------------------------:|:---------------------:
/paintBoard/board | GET                                  | 获取绘板内容
/paintBoard/paint | POST(x: Int, y: Int, color: Int)     | 进行绘画
/paintBoard/ws    | WebSocket                            | 监听绘板变化的 WebSocket
/paintBoard/save  | POST(password: String, path: String) | 保存绘板到本地
/paintBoard/load  | POST(password: String, path: String) | 从本地加载绘板（不会发送 WebSocket 信息）

## How to RUN it

正确配置 JDK 后，在项目根目录运行：

```bash
./gradlew run
```

会在 `localhost:8080` 开启一个服务端。