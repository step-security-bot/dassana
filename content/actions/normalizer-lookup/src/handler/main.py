from glob import glob

from aws_lambda_powertools.utilities.parser import event_parser
from aws_lambda_powertools.utilities.typing import LambdaContext
from aws_lambda_powertools.utilities.validation import validator
from pydantic import BaseModel
from dassana.common.models import NormalizedOutput, AlertClassification
from yaml import safe_load
from json import loads, load
from .models import WORKFLOW_DIR, INPUT_JSON_PATH


class Input(BaseModel):
    input: NormalizedOutput


def find_policy(file_path):
    with open(file_path, 'r') as f:
        yml = safe_load(f)
        return yml


def find_policy_match(yml, vendor_id="", policy_id=""):
    filters = yml.get('filters', [])
    if len(filters) == 0:
        return
    for fil in filter(lambda x: x.get('vendor', '') == vendor_id, filters):
        if policy_id in fil.get('policies', []):
            return yml.get('service'), yml.get('resource-type'), yml.get('csp'), AlertClassification(
                classRh=yml.get('class'),
                subclass=yml.get('subclass'),
                category=yml.get('category'),
                subcategory=yml.get('subcategory', '')
            ), yml.get('class')
    return


file_paths = glob(WORKFLOW_DIR, recursive=True)
policy_dict = dict(zip(file_paths, map(find_policy, file_paths)))

with open(INPUT_JSON_PATH, 'r') as f:
    schema = load(f)


@validator(inbound_schema=schema)
@event_parser(model=Input)
def handle(event: Input, context: LambdaContext):
    # If the input is missing resourceType, service, csp, OR any fields from alertClassification, then we proceed
    # with lookup in the specified workflow directory.
    if event.input.resourceType is not None and event.input.service is not None and event.input.csp is not None and \
            not any(event.input.alertClassification.__dict__.keys()):
        return loads(event.input.json())
    service, resource_type, csp, rh, class_ = next(filter(lambda x: x is not None,
                                                          map(find_policy_match,
                                                              policy_dict.values(),
                                                              [event.input.vendorId] * len(policy_dict),
                                                              [event.input.vendorPolicy] * len(policy_dict))),
                                                   (None, None, None, None, None))
    event.input.service = service if service is not None else event.input.service
    event.input.resourceType = resource_type if service is not None else event.input.resourceType

    if rh:
        event.input.alertClassification = rh if rh is not None else event.input.alertClassification
        # Pydantic serialization bug on alias "class", so the below line handles for aforementioned case
        event.input.alertClassification.classRh = class_ if class_ is not None \
            else event.input.alertClassification.classRh
    return loads(event.input.json(by_alias=True))
