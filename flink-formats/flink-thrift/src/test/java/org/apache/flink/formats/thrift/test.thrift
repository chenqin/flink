namespace java test.thrift

enum Operation {
  ADD = 1,
  SUBTRACT = 2,
  MULTIPLY = 3,
  DIVIDE = 4
}

struct Item {
	1: map<string, Operation> details
}

struct Work {
  1: i32 num1 = 0,
  2: i32 num2,
  3: Operation op,
  4: optional string comment,
  5: optional binary buffer
  6: double num3 = 0,
  7: bool sign1 = false,
  8: byte sign2,
  9: i16 num4,
  10: i64 num5,
  11: list<Operation> ops
  12: set<Item> items
  13: map<string, Item> index
}
