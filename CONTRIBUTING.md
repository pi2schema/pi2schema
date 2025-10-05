# Contributing to pi2schema

First off, thank you for considering contributing to pi2schema! It's people like you that make pi2schema such a great tool.

## Where do I go from here?

If you've noticed a bug or have a feature request, [make one](https://github.com/pi2schema/pi2schema/issues/new)! It's generally best if you get confirmation of your bug or approval for your feature request this way before starting to code.

### Fork & create a branch

If you decide to fix a bug or implement a feature, make sure to check out the master branch and make your changes in a separate branch.

### Get the code running

Please see the [Getting started](README.md#getting-started) section of the README for instructions on how to get the project running.

### Make your change

Make your change. Add tests for your change. Make the tests pass.

### Create a Pull Request

At this point, you should switch back to your master branch and make sure it's up to date with pi2schema's master branch.

```
git remote add upstream git@github.com:pi2schema/pi2schema.git
git checkout master
git pull upstream master
```

Then update your feature branch from your local copy of master, and push it!

```
git checkout <your-branch-name>
git rebase master
git push --force-with-lease <your-username> <your-branch-name>
```

Finally, go to GitHub and make a Pull Request.

## Code of Conduct

By participating in this project, you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md).
