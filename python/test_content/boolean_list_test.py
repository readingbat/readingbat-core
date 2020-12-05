def flip(x):
    for i in range(len(x)):
        x[i] = not x[i]
    return x


def main():
    print(flip([True, True]))
    print(flip([False]))
    print(flip([True]))
    print(flip([]))


if __name__ == '__main__':
    main()
