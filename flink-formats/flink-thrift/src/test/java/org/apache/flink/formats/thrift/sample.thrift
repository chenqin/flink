namespace java org.apache.flink.flinkformats.flinkthrift.tests

enum Operation {
    ADD = 1,
    SUBTRACT = 2,
    MULTIPLY = 3,
    DIVIDE = 4
}

struct Task {
    4: i64 taskid,
    1: bool isurgent,
    2: optional string date,
    3: optional i64 workid
}

struct Work {
    8: i64 workid,
    1: i32 num1 = 0,
    2: i32 num2,
    3: Operation op,
    4: optional string comment
    5: optional list<string> dates
    6: optional set<Task> tasks
    7: optional map<i64, i64> mapping
    10: optional binary codes
}

struct InvalidFieldWork {
    8: i64 workid,
    1: i32 num1 = 0,
    2: i32 num2,
    3: Operation op,
    4: optional i64 comment
    5: optional list<string> dates
    6: optional set<Task> tasks
    7: optional map<i64, i64> mapping
    10: optional binary codes
}
