# @desc Predict the result...

def decayed_word(word):
    result = ""
    for i in range(len(word)):
        if i % 2 == 0:
            result = result + word[i]
    return result


def main():
    print(decayed_word("Hello"))
    print(decayed_word("World"))
    print(decayed_word("Hi, I am me :)"))
    print(decayed_word("AAAAaAA"))


if __name__ == '__main__':
    main()
