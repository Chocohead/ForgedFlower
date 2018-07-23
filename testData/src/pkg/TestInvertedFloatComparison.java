package pkg;

public class TestInvertedFloatComparison
{
  public boolean less(double a, double b) {
    return a < b;
  }

  public boolean less(int a, int b) {
    return a < b;
  }

  public boolean notLess(double a, double b) {
    return !(a < b);
  }

  public boolean notLess(int a, int b) {
    return !(a < b);
  }

  public boolean greater(double a, double b) {
    return a > b;
  }

  public boolean greater(int a, int b) {
    return a > b;
  }

  public boolean notGreater(double a, double b) {
    return !(a > b);
  }

  public boolean notGreater(int a, int b) {
    return !(a > b);
  }

  public boolean lessEqual(double a, double b) {
    return a <= b;
  }

  public boolean lessEqual(int a, int b) {
    return a <= b;
  }

  public boolean notLessEqual(double a, double b) {
    return !(a <= b);
  }

  public boolean notLessEqual(int a, int b) {
    return !(a <= b);
  }

  public boolean greaterEqual(double a, double b) {
    return a >= b;
  }

  public boolean greaterEqual(int a, int b) {
    return a >= b;
  }

  public boolean notGreaterEqual(double a, double b) {
    return !(a >= b);
  }

  public boolean notGreaterEqual(int a, int b) {
    return !(a >= b);
  }
}