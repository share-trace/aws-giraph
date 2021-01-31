import asyncio
import datetime
import os
from base64 import b64decode
from typing import NoReturn

import boto3
import codetiming
import jsonpickle

import backend
import pda
import propagation
import search

stdout = backend.STDOUT
stderr = backend.STDERR

lambda_client = boto3.client('lambda')
kms_client = boto3.client('kms')


def _decrypt(value: str) -> str:
	"""Decrypts an encrypted environment variable"""
	context = {'LambdaFunctionName': os.environ['AWS_LAMBDA_FUNCTION_NAME']}
	decrypted = kms_client.decrypt(
		CiphertextBlob=b64decode(value), EncryptionContext=context)
	return decrypted('Plaintext').decode('utf-8')


# PDA environment variables
_CONTRACT_ID = _decrypt(os.environ['CONTRACT_ID'])
_LONG_LIVED_TOKEN = _decrypt(os.environ['LONG_LIVED_TOKEN'])
_CLIENT_NAMESPACE = _decrypt(os.environ['CLIENT_NAMESPACE'])
_READ_SCORE_NAMESPACE = _decrypt(os.environ['READ_SCORE_NAMESPACE'])
_WRITE_SCORE_NAMESPACE = _decrypt(os.environ['WRITE_SCORE_NAMESPACE'])
_LOCATION_NAMESPACE = _decrypt(os.environ['READ_LOCATION_NAMESPACE'])
_TAKE_SCORES = int(os.environ['TAKE_SCORES'])
_TAKE_LOCATIONS = int(os.environ['TAKE_LOCATIONS'])
_KEYRING_URL = os.environ['KEYRING_URL']
_READ_URL = os.environ['READ_URL']
_WRITE_URL = os.environ['WRITE_URL']
_KWARGS = {
	'client_namespace': _CLIENT_NAMESPACE,
	'contract_id': _CONTRACT_ID,
	'keyring_url': _KEYRING_URL,
	'read_url': _READ_URL,
	'write_url': _WRITE_URL,
	'long_lived_token': _LONG_LIVED_TOKEN}
# Contact search environment variables
_MIN_DURATION = datetime.timedelta(seconds=float(os.environ['MIN_DURATION']))
# Propagation environment variables
_TRANSMISSION_RATE = float(os.environ['TRANSMISSION_RATE'])
_ITERATIONS = int(os.environ['ITERATIONS'])
_TOLERANCE = float(os.environ['TOLERANCE'])
_TIMESTAMP_BUFFER = datetime.timedelta(
	seconds=float(os.environ['TIMESTAMP_BUFFER']))
_MESSAGE_THRESHOLD = float(os.environ['MESSAGE_THRESHOLD'])
_TIME_CONSTANT = float(os.environ['TIME_CONSTANT'])


def handle(event, context):
	"""Communicates with user PDAs to compute exposure scores.

	Args:
		event: AWS scheduled CloudWatch Event. Format:
			{
				"account": "123456789012",
				"region": "us-east-2",
				"detail": {},
				"detail-type": "Scheduled Event",
				"source": "aws.events",
				"time": "2019-03-01T01:23:45Z",
				"id": "cdc73f9d-aea9-11e3-9d5a-835b769c0d9c",
				"resources": [
					"arn:aws:events:us-east-1:123456789012:rule/my-schedule"
				]
			}
		context: Contains various AWS Lambda runtime variables.

	Returns: Mapping that contains the status code of the execution.

	References:
		https://docs.aws.amazon.com/lambda/latest/dg/services-cloudwatchevents
		.html
		https://docs.aws.amazon.com/lambda/latest/dg/python-context.html
	"""
	environment = jsonpickle.encode(dict(**os.environ))
	stdout(f'## ENVIRONMENT VARIABLES\n{environment}')
	stdout(f'## EVENT\n{jsonpickle.encode(event)}')
	stdout(f'## CONTEXT\n{jsonpickle.encode(context)}')
	stdout('------------------START TASK------------------')
	asyncio.get_event_loop().run_until_complete(_ahandle())
	stdout('-------------------END TASK-------------------')
	return {'status_code': pda.SUCCESS_CODE}


# noinspection PyTypeChecker
@codetiming.Timer(text='Total task duration: {:0.6f} s', logger=stdout)
async def _ahandle() -> NoReturn:
	def compute(locs, users):
		contact_search = search.ContactSearch(min_duration=_MIN_DURATION)
		factors = contact_search(locs)
		prop = propagation.LocalBeliefPropagation(
			transmission_rate=_TRANSMISSION_RATE,
			iterations=_ITERATIONS,
			tolerance=_TOLERANCE,
			time_buffer=_TIMESTAMP_BUFFER,
			msg_threshold=_MESSAGE_THRESHOLD,
			time_constant=_TIME_CONSTANT)
		return prop(factors=factors, variables=users)

	async with pda.PdaContext(**_KWARGS) as p:
		token, hats = await p.get_token_and_hats()
		variables, locations = await asyncio.gather(
			p.get_scores(
				token,
				hats=hats,
				namespace=_READ_SCORE_NAMESPACE,
				take=_TAKE_SCORES),
			p.get_locations(
				token,
				hats=hats,
				namespace=_LOCATION_NAMESPACE,
				take=_TAKE_LOCATIONS))
	updated_scores = compute(locations, variables)
	async with pda.PdaContext(**_KWARGS) as p:
		await p.post_scores(
			token, scores=updated_scores, namespace=_WRITE_SCORE_NAMESPACE)
