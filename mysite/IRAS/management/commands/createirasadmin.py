from getpass import getpass

from django.contrib.auth.hashers import make_password
from django.core.management.base import BaseCommand, CommandError

from IRAS.models import RiesAdministrator


class Command(BaseCommand):
    help = "Create an IRAS administrator with an interactively entered password."

    def add_arguments(self, parser):
        parser.add_argument("account")

    def handle(self, *args, **options):
        account = options["account"].strip()
        if not account or len(account) > 60:
            raise CommandError("Account must contain 1-60 characters.")
        if RiesAdministrator.objects.filter(admin_account=account).exists():
            raise CommandError("Administrator already exists.")
        password = getpass("Password: ")
        confirmation = getpass("Confirm password: ")
        if password != confirmation or not 8 <= len(password) <= 256:
            raise CommandError("Passwords must match and contain 8-256 characters.")
        RiesAdministrator.objects.create(admin_account=account, password=make_password(password))
        self.stdout.write(self.style.SUCCESS("Administrator created."))