# PaintBoardServer

绘板服务端

[Have a try](http://106.15.202.51:8080/paintBoard)

## APIs

接口 | 方法及Body参数 | 详细内容
:------------------:|:----------------------------------:|:---------------------:
/paintBoard/board?id=[绘版id] | GET                                  | 获取绘板内容
/paintBoard/paint?id=[绘版id] | POST(x: Int, y: Int, color: Int)     | 进行绘画
/paintBoard/ws    | WebSocket                            | 监听绘板变化的 WebSocket
/paintBoard/history?id=[绘版id]&time=[查询时间点]  | GET(password: String) | **管理员**查询某一时刻绘版内容 
/paintBoard/blame?id=[绘版id]&time=[查询时间点]&x=[x坐标]&y=[y坐标]  | GET(password: String) | **管理员**查询某一时刻某位置像素由哪个用户在何时绘制 
/paintBoard/rollback?id=[绘版id]&time=[回滚时间点] | POST(password: String) | **管理员**将绘版回滚到某一时刻**此操作不可撤销** 

## How to RUN it

正确配置 JDK 后，在项目根目录运行：

```bash
./gradlew run
```

会在 `localhost:8080` 开启一个服务端。