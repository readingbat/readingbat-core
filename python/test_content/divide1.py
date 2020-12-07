# @desc Determine the value returned by the function.

def div(x):
    result = x / 2.0
    return result


def main():
    print(div(2))
    print(div(-3))
    print(div(9))
    print(div(10))
    print(div(1.5))


if __name__ == '__main__':
    main()
