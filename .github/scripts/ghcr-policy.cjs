// Fail closed unless the exact source commit passed this repository's push CI.
async function verifyCi(github, context, workflow) {
  const runs = await github.paginate(github.rest.actions.listWorkflowRuns, {
    ...context.repo, workflow_id: workflow, head_sha: context.sha,
    branch: 'dev', event: 'push', status: 'completed', per_page: 100,
  });
  if (!runs.some(run => run.head_sha === context.sha && run.head_branch === 'dev'
      && run.event === 'push' && run.conclusion === 'success'
      && run.head_repository?.id === context.payload.repository.id)) {
    throw new Error(`Successful dev push CI required for ${context.sha}. Wait for CI and retry.`);
  }
}

async function verifyPrivate(github, context, packages, allowMissing) {
  for (const package_name of packages) {
    let response;
    try {
      response = await github.request('GET /users/{username}/packages/{package_type}/{package_name}', {
        username: context.repo.owner, package_type: 'container', package_name,
      });
    } catch (error) {
      // A first publication creates a private package. Verify it again after push.
      if (allowMissing && error.status === 404) continue;
      throw error;
    }
    if (response.data.visibility !== 'private') throw new Error(`Package ${package_name} must be private. Publication stopped.`);
  }
}
module.exports = { verifyCi, verifyPrivate };
