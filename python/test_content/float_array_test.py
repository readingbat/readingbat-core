def inc1(x):
    for i in range(len(x)):
        x[i] = x[i] + 1.0
    return x


def main():
    print(inc1([3.4, 4.4]))
    print(inc1([4.4]))
    print(inc1([0.0]))
    print(inc1([]))


if __name__ == '__main__':
    main()
