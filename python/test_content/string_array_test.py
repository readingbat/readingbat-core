def flip(x):
    for i in range(len(x)):
        x[i] = x[i].upper()
    return x


def main():
    print(flip(["this", "is"]))
    print(flip(["a"]))
    print(flip(["test"]))
    print(flip([]))


if __name__ == '__main__':
    main()
